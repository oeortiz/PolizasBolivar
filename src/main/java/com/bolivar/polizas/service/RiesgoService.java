package com.bolivar.polizas.service;

import com.bolivar.polizas.domain.CalculadoraPrima;
import com.bolivar.polizas.entity.DatosRiesgo;
import com.bolivar.polizas.entity.IntegrationOperation;
import com.bolivar.polizas.entity.Participante;
import com.bolivar.polizas.entity.Poliza;
import com.bolivar.polizas.entity.Riesgo;
import com.bolivar.polizas.entity.enums.OperacionNegocio;
import com.bolivar.polizas.entity.enums.ResultadoAuditoria;
import com.bolivar.polizas.entity.enums.TipoEntidad;
import com.bolivar.polizas.exception.DominioException;
import com.bolivar.polizas.exception.RecursoNoEncontradoException;
import com.bolivar.polizas.integration.outbox.RegistroOperacionIntegracion;
import com.bolivar.polizas.integration.outbox.SolicitudMutacion;
import com.bolivar.polizas.repository.IntegrationOperationRepository;
import com.bolivar.polizas.repository.PolizaRepository;
import com.bolivar.polizas.repository.RiesgoRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Casos de uso de riesgos: agregar a una COLECTIVA y cancelar. Ambos bloquean la póliza,
 * aplican la regla en el agregado, actualizan la prima consolidada y registran la
 * mutación para el CORE en la misma transacción local.
 */
@Service
public class RiesgoService {

    private final PolizaRepository polizaRepository;
    private final RiesgoRepository riesgoRepository;
    private final IntegrationOperationRepository operacionRepository;
    private final RegistroOperacionIntegracion registroIntegracion;
    private final AuditoriaService auditoria;
    private final CalculadoraPrima calculadora;
    private final ContextoSolicitud contexto;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RiesgoService(PolizaRepository polizaRepository,
                         RiesgoRepository riesgoRepository,
                         IntegrationOperationRepository operacionRepository,
                         RegistroOperacionIntegracion registroIntegracion,
                         AuditoriaService auditoria,
                         CalculadoraPrima calculadora,
                         ContextoSolicitud contexto,
                         ObjectMapper objectMapper,
                         Clock clock) {
        this.polizaRepository = polizaRepository;
        this.riesgoRepository = riesgoRepository;
        this.operacionRepository = operacionRepository;
        this.registroIntegracion = registroIntegracion;
        this.auditoria = auditoria;
        this.calculadora = calculadora;
        this.contexto = contexto;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Agrega un riesgo. Solo COLECTIVA (la regla la aplica {@link Poliza#agregarRiesgo}).
     * Se sincroniza como MODIFICAR sobre la póliza (supuesto S-09).
     */
    @Transactional
    public ResultadoMutacion agregar(Long polizaId, DatosRiesgo datos, String claveCliente) {
        OperacionNegocio operacion = OperacionNegocio.AGREGAR_RIESGO;
        ClaveIdempotencia clave = ClaveIdempotencia.de(operacion, polizaId, claveCliente);
        Poliza poliza = polizaRepository.findByIdForUpdate(polizaId)
                .orElseThrow(() -> RecursoNoEncontradoException.poliza(polizaId));

        Optional<IntegrationOperation> previa = buscarPrevia(clave);
        if (previa.isPresent()) {
            Riesgo creado = riesgoRepository.findById(riesgoIdDe(previa.get()))
                    .orElseThrow(() -> new IllegalStateException("Operación sin riesgo asociado: " + previa.get().getId()));
            return new ResultadoMutacion(previa.get(), poliza, creado, true);
        }

        try {
            Riesgo riesgo = poliza.agregarRiesgo(datos, calculadora);
            riesgoRepository.save(riesgo);
            riesgo.marcarSincronizacionPendiente();
            poliza.marcarSincronizacionPendiente();

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("polizaId", poliza.getId());
            payload.put("riesgoId", riesgo.getId());
            payload.put("direccionInmueble", riesgo.getDireccionInmueble());
            payload.put("canonMensual", riesgo.getCanonMensual());
            payload.put("prima", riesgo.getPrima());
            payload.put("arrendatario", participante(riesgo.getArrendatario()));
            payload.put("arrendador", participante(riesgo.getArrendador()));
            payload.put("canonConsolidado", poliza.getCanonMensual());
            payload.put("primaConsolidada", poliza.getPrima());

            IntegrationOperation op = registroIntegracion.registrar(new SolicitudMutacion(
                    operacion, poliza.getId(), operacion.name(), LocalDate.now(clock), payload,
                    clave.valor(), contexto.correlationId()));

            auditoria.registrar(operacion.name(), TipoEntidad.RIESGO, riesgo.getId(),
                    ResultadoAuditoria.ACEPTADO_PENDIENTE, op.getId(),
                    Map.of("polizaId", poliza.getId(),
                            "prima", riesgo.getPrima(),
                            "primaConsolidada", poliza.getPrima()));
            return new ResultadoMutacion(op, poliza, riesgo, false);
        } catch (DominioException e) {
            Rechazos.auditar(auditoria, operacion.name(), TipoEntidad.POLIZA, polizaId, e);
            throw e;
        }
    }

    /**
     * Cancela un riesgo: queda CANCELADO (sin borrado), deja de sumar en la prima
     * consolidada y se registra ANULAR sobre el riesgo para el CORE.
     */
    @Transactional
    public ResultadoMutacion cancelar(Long riesgoId, String motivo, String claveCliente) {
        OperacionNegocio operacion = OperacionNegocio.CANCELAR_RIESGO;
        ClaveIdempotencia clave = ClaveIdempotencia.de(operacion, riesgoId, claveCliente);
        // Primero se bloquea la póliza y después se lee el riesgo, para serializar
        // las mutaciones del mismo agregado.
        Long polizaId = riesgoRepository.findPolizaIdByRiesgoId(riesgoId)
                .orElseThrow(() -> RecursoNoEncontradoException.riesgo(riesgoId));
        Poliza poliza = polizaRepository.findByIdForUpdate(polizaId)
                .orElseThrow(() -> RecursoNoEncontradoException.poliza(polizaId));
        Riesgo riesgo = poliza.getRiesgos().stream()
                .filter(r -> r.getId().equals(riesgoId))
                .findFirst()
                .orElseThrow(() -> RecursoNoEncontradoException.riesgo(riesgoId));

        Optional<IntegrationOperation> previa = buscarPrevia(clave);
        if (previa.isPresent()) {
            return new ResultadoMutacion(previa.get(), poliza, riesgo, true);
        }

        try {
            Instant ahora = clock.instant();
            poliza.cancelarRiesgo(riesgo, ahora, motivo, calculadora);
            riesgo.marcarSincronizacionPendiente();

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("riesgoId", riesgo.getId());
            payload.put("polizaId", poliza.getId());
            payload.put("fechaCancelacion", ahora.toString());
            payload.put("motivo", motivo);
            payload.put("canonConsolidado", poliza.getCanonMensual());
            payload.put("primaConsolidada", poliza.getPrima());

            IntegrationOperation op = registroIntegracion.registrar(new SolicitudMutacion(
                    operacion, riesgo.getId(), operacion.name(), LocalDate.now(clock), payload,
                    clave.valor(), contexto.correlationId()));

            auditoria.registrar(operacion.name(), TipoEntidad.RIESGO, riesgo.getId(),
                    ResultadoAuditoria.ACEPTADO_PENDIENTE, op.getId(),
                    Map.of("polizaId", poliza.getId(),
                            "primaRiesgo", riesgo.getPrima(),
                            "primaConsolidada", poliza.getPrima()));
            return new ResultadoMutacion(op, poliza, riesgo, false);
        } catch (DominioException e) {
            Rechazos.auditar(auditoria, operacion.name(), TipoEntidad.RIESGO, riesgoId, e);
            throw e;
        }
    }

    private Optional<IntegrationOperation> buscarPrevia(ClaveIdempotencia clave) {
        return clave.provistaPorCliente()
                ? operacionRepository.findByIdempotencyKey(clave.valor())
                : Optional.empty();
    }

    private Long riesgoIdDe(IntegrationOperation op) {
        try {
            JsonNode nodo = objectMapper.readTree(op.getPayload()).get("riesgoId");
            if (nodo == null || !nodo.canConvertToLong()) {
                throw new IllegalStateException("El payload de la operación " + op.getId() + " no tiene riesgoId");
            }
            return nodo.asLong();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Payload ilegible en la operación " + op.getId(), e);
        }
    }

    private static Map<String, Object> participante(Participante p) {
        return Map.of("tipoDocumento", p.tipoDocumento().name(),
                "numeroDocumento", p.numeroDocumento(),
                "nombre", p.nombre());
    }
}
