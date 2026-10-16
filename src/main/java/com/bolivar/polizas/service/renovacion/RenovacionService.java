package com.bolivar.polizas.service.renovacion;

import com.bolivar.polizas.domain.CalculadoraPrima;
import com.bolivar.polizas.entity.IntegrationOperation;
import com.bolivar.polizas.entity.Poliza;
import com.bolivar.polizas.entity.RenewalHistory;
import com.bolivar.polizas.entity.RenovacionRiesgo;
import com.bolivar.polizas.entity.ResultadoRenovacion;
import com.bolivar.polizas.entity.enums.EstadoIntegracion;
import com.bolivar.polizas.entity.enums.OperacionNegocio;
import com.bolivar.polizas.entity.enums.ResultadoAuditoria;
import com.bolivar.polizas.entity.enums.TipoEntidad;
import com.bolivar.polizas.exception.CodigoError;
import com.bolivar.polizas.exception.DominioException;
import com.bolivar.polizas.exception.RecursoNoEncontradoException;
import com.bolivar.polizas.exception.ReglaNegocioException;
import com.bolivar.polizas.integration.outbox.RegistroOperacionIntegracion;
import com.bolivar.polizas.integration.outbox.SolicitudMutacion;
import com.bolivar.polizas.repository.IntegrationOperationRepository;
import com.bolivar.polizas.repository.PolizaRepository;
import com.bolivar.polizas.repository.RenewalHistoryRepository;
import com.bolivar.polizas.service.AuditoriaService;
import com.bolivar.polizas.service.ClaveIdempotencia;
import com.bolivar.polizas.service.ContextoSolicitud;
import com.bolivar.polizas.service.ipc.IpcAplicable;
import com.bolivar.polizas.service.ipc.IpcService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Caso de uso POST /polizas/{id}/renovar.
 *
 * <ol>
 *   <li>Bloquea la póliza (FOR UPDATE): renovaciones concurrentes se serializan.</li>
 *   <li>Con {@code Idempotency-Key} repetida devuelve el historial y la operación originales.</li>
 *   <li>Valida el estado (CANCELADA → 409) antes de consultar el IPC.</li>
 *   <li>Rechaza si hay una renovación anterior no confirmada por el CORE (RENOVACION_EN_CURSO):
 *       evita encadenar renovaciones por reintentos sin clave.</li>
 *   <li>Resuelve el IPC del periodo de la nueva vigencia (falla de forma controlada si falta).</li>
 *   <li>Renueva el agregado: canon con IPC, prima recalculada, vigencia desplazada, RENOVADA.</li>
 *   <li>En la misma transacción: operación MODIFICAR para el CORE, historial inmutable y auditoría.</li>
 * </ol>
 * Las restricciones únicas de {@code renewal_history} (clave idempotente y periodo por póliza)
 * son la última barrera ante duplicados.
 */
@Service
public class RenovacionService {

    private static final Logger log = LoggerFactory.getLogger(RenovacionService.class);
    private static final OperacionNegocio OPERACION = OperacionNegocio.RENOVAR_POLIZA;

    private final PolizaRepository polizaRepository;
    private final RenewalHistoryRepository historialRepository;
    private final IntegrationOperationRepository operacionRepository;
    private final RegistroOperacionIntegracion registroIntegracion;
    private final IpcService ipcService;
    private final AuditoriaService auditoria;
    private final CalculadoraPrima calculadora;
    private final ContextoSolicitud contexto;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RenovacionService(PolizaRepository polizaRepository,
                             RenewalHistoryRepository historialRepository,
                             IntegrationOperationRepository operacionRepository,
                             RegistroOperacionIntegracion registroIntegracion,
                             IpcService ipcService,
                             AuditoriaService auditoria,
                             CalculadoraPrima calculadora,
                             ContextoSolicitud contexto,
                             ObjectMapper objectMapper,
                             Clock clock) {
        this.polizaRepository = polizaRepository;
        this.historialRepository = historialRepository;
        this.operacionRepository = operacionRepository;
        this.registroIntegracion = registroIntegracion;
        this.ipcService = ipcService;
        this.auditoria = auditoria;
        this.calculadora = calculadora;
        this.contexto = contexto;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public ResultadoRenovacionPoliza renovar(Long polizaId, String claveCliente) {
        ClaveIdempotencia clave = ClaveIdempotencia.de(OPERACION, polizaId, claveCliente);
        Poliza poliza = polizaRepository.findByIdForUpdate(polizaId)
                .orElseThrow(() -> RecursoNoEncontradoException.poliza(polizaId));

        if (clave.provistaPorCliente()) {
            Optional<RenewalHistory> previo = historialRepository.findByIdempotencyKey(clave.valor());
            if (previo.isPresent()) {
                RenewalHistory historial = previo.get();
                IntegrationOperation op = operacionRepository.findById(historial.getIntegrationOperationId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Historial " + historial.getId() + " sin operación de integración"));
                return new ResultadoRenovacionPoliza(poliza, historial, op, true);
            }
        }

        try {
            // Orden del enunciado: existencia → estado → IPC.
            poliza.verificarPermiteRenovar();
            verificarSinRenovacionPendiente(polizaId);

            LocalDate hoy = LocalDate.now(clock);
            IpcAplicable ipc = ipcService.obtener(poliza.getVigencia().siguiente().fechaInicio(), hoy);
            ResultadoRenovacion resultado = poliza.renovar(ipc.porcentaje(), calculadora);
            poliza.marcarSincronizacionPendiente();

            IntegrationOperation op = registroIntegracion.registrar(new SolicitudMutacion(
                    OPERACION, poliza.getId(), OPERACION.name(), resultado.nueva().fechaInicioVigencia(),
                    payload(poliza, resultado, ipc), clave.valor(), contexto.correlationId()));

            RenewalHistory historial = historialRepository.save(new RenewalHistory(
                    poliza, clave.valor(), op.getId(), resultado.anterior(), resultado.nueva(),
                    ipc.porcentaje(), ipc.constanteNombre(), poliza.getDuracionMeses(),
                    json(resultado.riesgos()), contexto.actor()));

            auditoria.registrar(OPERACION.name(), TipoEntidad.POLIZA, poliza.getId(),
                    ResultadoAuditoria.ACEPTADO_PENDIENTE, op.getId(), Map.of(
                            "ipcAplicado", ipc.porcentaje(),
                            "ipcConstante", ipc.constanteNombre(),
                            "primaAnterior", resultado.anterior().prima(),
                            "primaNueva", resultado.nueva().prima(),
                            "vigenciaNueva", resultado.nueva().fechaInicioVigencia() + "/"
                                    + resultado.nueva().fechaFinVigencia()));
            log.info("Póliza {} renovada localmente con IPC {} ({}); operación {} pendiente de CORE",
                    poliza.getId(), ipc.porcentaje(), ipc.constanteNombre(), op.getId());
            return new ResultadoRenovacionPoliza(poliza, historial, op, false);
        } catch (DominioException e) {
            auditarRechazo(polizaId, e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<RenewalHistory> historial(Long polizaId) {
        if (!polizaRepository.existsById(polizaId)) {
            throw RecursoNoEncontradoException.poliza(polizaId);
        }
        return historialRepository.findByPolizaIdOrderByIdAsc(polizaId);
    }

    /**
     * No se permite una nueva renovación mientras la anterior no esté CONFIRMADA por el CORE
     * (PENDIENTE, EN_PROCESO, FALLIDA o RECHAZADA). Así un reintento sin {@code Idempotency-Key}
     * no encadena una segunda renovación, y un fallo de integración se revisa antes de seguir.
     */
    private void verificarSinRenovacionPendiente(Long polizaId) {
        if (operacionRepository.existsByEntityTypeAndEntityIdAndOperacionNegocioAndEstadoNot(
                TipoEntidad.POLIZA, polizaId, OPERACION, EstadoIntegracion.CONFIRMADA)) {
            throw new ReglaNegocioException(CodigoError.RENOVACION_EN_CURSO,
                    "La póliza tiene una renovación anterior pendiente de confirmación del CORE");
        }
    }

    private void auditarRechazo(Long polizaId, DominioException e) {
        try {
            auditoria.registrarRechazo(OPERACION.name(), TipoEntidad.POLIZA, polizaId, e);
        } catch (RuntimeException ex) {
            log.warn("No se pudo auditar el rechazo de la renovación de la póliza {}", polizaId, ex);
        }
    }

    private static Map<String, Object> payload(Poliza poliza, ResultadoRenovacion r, IpcAplicable ipc) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("polizaId", poliza.getId());
        payload.put("tipoPoliza", poliza.getTipo().name());
        payload.put("ipcAplicado", ipc.porcentaje());
        payload.put("fechaInicioVigencia", r.nueva().fechaInicioVigencia().toString());
        payload.put("fechaFinVigencia", r.nueva().fechaFinVigencia().toString());
        payload.put("duracionMeses", poliza.getDuracionMeses());
        payload.put("canonMensual", r.nueva().canonMensual());
        payload.put("prima", r.nueva().prima());
        payload.put("riesgos", r.riesgos().stream().map(x -> Map.of(
                "riesgoId", x.riesgoId(),
                "canonMensual", x.canonNuevo(),
                "prima", x.primaNueva())).toList());
        return payload;
    }

    private String json(List<RenovacionRiesgo> riesgos) {
        try {
            return objectMapper.writeValueAsString(riesgos);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el detalle de riesgos", e);
        }
    }
}
