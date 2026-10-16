package com.bolivar.polizas.service;

import com.bolivar.polizas.domain.CalculadoraPrima;
import com.bolivar.polizas.domain.Vigencia;
import com.bolivar.polizas.entity.DatosRiesgo;
import com.bolivar.polizas.entity.IntegrationOperation;
import com.bolivar.polizas.entity.Participante;
import com.bolivar.polizas.entity.Poliza;
import com.bolivar.polizas.entity.Riesgo;
import com.bolivar.polizas.entity.enums.EstadoPoliza;
import com.bolivar.polizas.entity.enums.OperacionNegocio;
import com.bolivar.polizas.entity.enums.ResultadoAuditoria;
import com.bolivar.polizas.entity.enums.TipoEntidad;
import com.bolivar.polizas.entity.enums.TipoPoliza;
import com.bolivar.polizas.exception.DominioException;
import com.bolivar.polizas.exception.RecursoNoEncontradoException;
import com.bolivar.polizas.exception.ValorInvalidoException;
import com.bolivar.polizas.integration.outbox.RegistroOperacionIntegracion;
import com.bolivar.polizas.integration.outbox.SolicitudMutacion;
import com.bolivar.polizas.repository.IntegrationOperationRepository;
import com.bolivar.polizas.repository.PolizaRepository;
import com.bolivar.polizas.repository.PolizaSpecifications;
import com.bolivar.polizas.repository.RiesgoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Casos de uso de pólizas: alta, consultas y cancelación. */
@Service
public class PolizaService {

    private final PolizaRepository polizaRepository;
    private final RiesgoRepository riesgoRepository;
    private final IntegrationOperationRepository operacionRepository;
    private final RegistroOperacionIntegracion registroIntegracion;
    private final AuditoriaService auditoria;
    private final CalculadoraPrima calculadora;
    private final ContextoSolicitud contexto;
    private final Clock clock;

    public PolizaService(PolizaRepository polizaRepository,
                         RiesgoRepository riesgoRepository,
                         IntegrationOperationRepository operacionRepository,
                         RegistroOperacionIntegracion registroIntegracion,
                         AuditoriaService auditoria,
                         CalculadoraPrima calculadora,
                         ContextoSolicitud contexto,
                         Clock clock) {
        this.polizaRepository = polizaRepository;
        this.riesgoRepository = riesgoRepository;
        this.operacionRepository = operacionRepository;
        this.registroIntegracion = registroIntegracion;
        this.auditoria = auditoria;
        this.calculadora = calculadora;
        this.contexto = contexto;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<Poliza> listar(TipoPoliza tipo, EstadoPoliza estado, int pagina, int tamano) {
        return polizaRepository.findAll(PolizaSpecifications.conFiltros(tipo, estado),
                PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.ASC, "id")));
    }

    @Transactional(readOnly = true)
    public Poliza obtener(Long id) {
        return polizaRepository.findById(id).orElseThrow(() -> RecursoNoEncontradoException.poliza(id));
    }

    /** Todos los riesgos de la póliza (activos y cancelados), en orden de alta. */
    @Transactional(readOnly = true)
    public List<Riesgo> riesgos(Long polizaId) {
        if (!polizaRepository.existsById(polizaId)) {
            throw RecursoNoEncontradoException.poliza(polizaId);
        }
        return riesgoRepository.findByPolizaIdOrderByIdAsc(polizaId);
    }

    // ------------------------------------------------------------------
    // Alta (endpoint adicional, supuesto S-14/S-15: no genera mutación al CORE)
    // ------------------------------------------------------------------

    /**
     * INDIVIDUAL: exactamente un riesgo y sin tomador explícito (el tomador es el arrendatario).
     * COLECTIVA: tomador obligatorio y al menos un riesgo.
     */
    @Transactional
    public Poliza crear(TipoPoliza tipo, Participante tomador, LocalDate fechaInicio, int duracionMeses,
                        List<DatosRiesgo> riesgos) {
        if (tipo == null) {
            throw new ValorInvalidoException("El tipo de póliza es obligatorio");
        }
        Vigencia vigencia = Vigencia.desde(fechaInicio, duracionMeses);
        List<DatosRiesgo> lista = riesgos == null ? List.of() : riesgos;
        Poliza poliza = switch (tipo) {
            case INDIVIDUAL -> {
                if (lista.size() != 1) {
                    throw new ValorInvalidoException("Una póliza INDIVIDUAL se crea con exactamente un riesgo");
                }
                if (tomador != null) {
                    throw new ValorInvalidoException(
                            "En una póliza INDIVIDUAL el tomador es el arrendatario: omita el campo tomador");
                }
                yield Poliza.crearIndividual(lista.getFirst(), vigencia, calculadora);
            }
            case COLECTIVA -> {
                if (tomador == null) {
                    throw new ValorInvalidoException("El tomador es obligatorio en una póliza COLECTIVA");
                }
                yield Poliza.crearColectiva(tomador, vigencia, lista, calculadora);
            }
        };
        return guardarNueva(poliza);
    }

    private Poliza guardarNueva(Poliza poliza) {
        Poliza guardada = polizaRepository.save(poliza);
        auditoria.registrar("CREAR_POLIZA", TipoEntidad.POLIZA, guardada.getId(), ResultadoAuditoria.EXITO, null,
                Map.of("tipo", guardada.getTipo().name(),
                        "riesgos", guardada.getRiesgos().size(),
                        "prima", guardada.getPrima()));
        return guardada;
    }

    // ------------------------------------------------------------------
    // Cancelación
    // ------------------------------------------------------------------

    /**
     * Cancela la póliza localmente y registra la mutación ANULAR para el CORE en la misma
     * transacción. La sincronización queda pendiente (HTTP 202). Con la misma
     * {@code Idempotency-Key} devuelve la operación original sin repetir nada.
     */
    @Transactional
    public ResultadoMutacion cancelar(Long polizaId, String motivo, String claveCliente) {
        OperacionNegocio operacion = OperacionNegocio.CANCELAR_POLIZA;
        ClaveIdempotencia clave = ClaveIdempotencia.de(operacion, polizaId, claveCliente);
        Poliza poliza = polizaRepository.findByIdForUpdate(polizaId)
                .orElseThrow(() -> RecursoNoEncontradoException.poliza(polizaId));

        Optional<IntegrationOperation> previa = buscarPrevia(clave);
        if (previa.isPresent()) {
            return new ResultadoMutacion(previa.get(), poliza, null, true);
        }

        try {
            Instant ahora = clock.instant();
            EstadoPoliza estadoAnterior = poliza.getEstado();
            List<Long> riesgosCancelados = poliza.riesgosActivos().stream().map(Riesgo::getId).toList();
            poliza.cancelar(ahora, motivo);
            poliza.marcarSincronizacionPendiente();

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("polizaId", poliza.getId());
            payload.put("tipoPoliza", poliza.getTipo().name());
            payload.put("fechaCancelacion", ahora.toString());
            payload.put("motivo", motivo);
            payload.put("riesgosCancelados", riesgosCancelados);

            IntegrationOperation op = registroIntegracion.registrar(new SolicitudMutacion(
                    operacion, poliza.getId(), operacion.name(), LocalDate.now(clock), payload,
                    clave.valor(), contexto.correlationId()));

            auditoria.registrar(operacion.name(), TipoEntidad.POLIZA, poliza.getId(),
                    ResultadoAuditoria.ACEPTADO_PENDIENTE, op.getId(),
                    Map.of("estadoAnterior", estadoAnterior.name(),
                            "riesgosCancelados", riesgosCancelados));
            return new ResultadoMutacion(op, poliza, null, false);
        } catch (DominioException e) {
            Rechazos.auditar(auditoria, operacion.name(), TipoEntidad.POLIZA, polizaId, e);
            throw e;
        }
    }

    private Optional<IntegrationOperation> buscarPrevia(ClaveIdempotencia clave) {
        return clave.provistaPorCliente()
                ? operacionRepository.findByIdempotencyKey(clave.valor())
                : Optional.empty();
    }
}
