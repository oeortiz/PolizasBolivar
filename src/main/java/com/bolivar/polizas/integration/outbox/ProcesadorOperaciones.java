package com.bolivar.polizas.integration.outbox;

import com.bolivar.polizas.config.CorrelationIdFilter;
import com.bolivar.polizas.config.PolizasProperties;
import com.bolivar.polizas.entity.IntegrationOperation;
import com.bolivar.polizas.entity.enums.AdaptadorIntegracion;
import com.bolivar.polizas.entity.enums.ResultadoAuditoria;
import com.bolivar.polizas.integration.contract.ConstructorSolicitudCore;
import com.bolivar.polizas.integration.port.CoreMutationPort;
import com.bolivar.polizas.integration.port.ResultadoEnvio;
import com.bolivar.polizas.integration.port.SolicitudCore;
import com.bolivar.polizas.observability.MetricasIntegracion;
import com.bolivar.polizas.repository.IntegrationOperationRepository;
import com.bolivar.polizas.service.AuditoriaService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Procesador del outbox: envía las mutaciones pendientes al CORE a través del puerto
 * {@link CoreMutationPort} y aplica la política de reintentos.
 *
 * <h2>Flujo de un intento</h2>
 * <ol>
 *   <li><b>Reserva</b> (transacción corta): {@code FOR UPDATE SKIP LOCKED} sobre operaciones
 *       PENDIENTE vencidas → EN_PROCESO, {@code intentos + 1}. Ninguna otra instancia las toma.</li>
 *   <li><b>Envío</b> (sin transacción abierta): un único intento HTTP con la misma
 *       {@code Idempotency-Key} y el mismo {@code correlationId} de siempre.</li>
 *   <li><b>Registro</b> (transacción corta): según el {@link ResultadoEnvio}:
 *     <ul>
 *       <li>Confirmado → CONFIRMADA (única vía hacia "sincronizada").</li>
 *       <li>Rechazado → RECHAZADA, sin reintentos.</li>
 *       <li>FalloTransitorio → reintento en {@code intervaloReintento} (1 min) o FALLIDA si se
 *           agotaron los intentos (1 + 3).</li>
 *       <li>Incierto → igual que transitorio si {@code reintentarResultadosInciertos}; si no,
 *           FALLIDA para revisión (evita duplicar en un CORE sin idempotencia).</li>
 *     </ul>
 *     Se recalcula el estado de sincronización de la póliza/riesgo y se audita el desenlace.</li>
 * </ol>
 *
 * <h2>Recuperación</h2>
 * Las operaciones PENDIENTE sobreviven a un reinicio por estar persistidas. Las que quedaron
 * EN_PROCESO por una caída se detectan por antigüedad y se tratan como resultado incierto.
 *
 * <p>No garantiza "exactamente una vez" entre sistemas: garantiza al menos un intento, intentos
 * acotados, clave idempotente estable y que nada se marca sincronizado sin confirmación.</p>
 */
@Component
public class ProcesadorOperaciones {

    private static final Logger log = LoggerFactory.getLogger(ProcesadorOperaciones.class);

    private final IntegrationOperationRepository repository;
    private final CoreMutationPort port;
    private final ConstructorSolicitudCore constructor;
    private final SincronizacionEntidades sincronizacion;
    private final AuditoriaService auditoria;
    private final TransactionTemplate tx;
    private final PolizasProperties.Procesador config;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final MetricasIntegracion metricas;

    public ProcesadorOperaciones(IntegrationOperationRepository repository,
                                 CoreMutationPort port,
                                 ConstructorSolicitudCore constructor,
                                 SincronizacionEntidades sincronizacion,
                                 AuditoriaService auditoria,
                                 TransactionTemplate tx,
                                 PolizasProperties properties,
                                 ObjectMapper objectMapper,
                                 Clock clock,
                                 MetricasIntegracion metricas) {
        this.repository = repository;
        this.port = port;
        this.constructor = constructor;
        this.sincronizacion = sincronizacion;
        this.auditoria = auditoria;
        this.tx = tx;
        this.config = properties.integracion().procesador();
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.metricas = metricas;
        validarUmbral(properties);
    }

    /** Procesa un lote de operaciones vencidas. Devuelve cuántas se intentaron. */
    public int procesarPendientes() {
        List<SolicitudCore> reservadas = tx.execute(estado -> {
            List<SolicitudCore> lista = new ArrayList<>();
            for (IntegrationOperation op : repository.reservarPendientes(clock.instant(), config.tamanoLote())) {
                lista.add(iniciar(op));
            }
            return lista;
        });
        reservadas.forEach(this::enviarYRegistrar);
        return reservadas.size();
    }

    /** Intenta una operación concreta si está PENDIENTE y vencida (disparo tras commit). */
    public boolean procesarOperacion(UUID operacionId) {
        Optional<SolicitudCore> reservada = tx.execute(estado ->
                repository.reservarPendiente(operacionId, clock.instant()).map(this::iniciar));
        reservada.ifPresent(this::enviarYRegistrar);
        return reservada.isPresent();
    }

    /**
     * Reprograma o cierra las operaciones EN_PROCESO abandonadas por un proceso que se cayó.
     * Su resultado es desconocido: se tratan como {@link ResultadoEnvio.Incierto}.
     */
    public int recuperarInterrumpidas() {
        Integer recuperadas = tx.execute(estado -> {
            Instant limite = clock.instant().minus(config.umbralOperacionInterrumpida());
            List<IntegrationOperation> ops = repository.reservarInterrumpidas(limite);
            for (IntegrationOperation op : ops) {
                log.warn("Operación {} interrumpida en EN_PROCESO desde {}: se trata como resultado incierto",
                        op.getId(), op.getUltimoIntentoAt());
                aplicar(op, new ResultadoEnvio.Incierto(null,
                        "Procesamiento interrumpido (reinicio o caída) con resultado desconocido", op.getAdaptador()));
            }
            return ops.size();
        });
        return recuperadas == null ? 0 : recuperadas;
    }

    // ------------------------------------------------------------------

    private SolicitudCore iniciar(IntegrationOperation op) {
        op.iniciarIntento(clock.instant(), port.tipo());
        return constructor.construir(op);
    }

    private void enviarYRegistrar(SolicitudCore solicitud) {
        UUID id = solicitud.idempotencyKey();
        MDC.put(CorrelationIdFilter.MDC_KEY, solicitud.request().header().correlationId().toString());
        try {
            ResultadoEnvio resultado;
            long inicio = System.nanoTime();
            try {
                resultado = port.enviar(solicitud);
            } catch (RuntimeException e) {
                // El puerto no debería lanzar; si lo hace, no se sabe si la mutación salió.
                log.error("El adaptador {} lanzó una excepción con la operación {}", port.tipo(), id, e);
                resultado = new ResultadoEnvio.Incierto(null,
                        "Error inesperado en el adaptador: " + e.getClass().getSimpleName(), port.tipo());
            }
            metricas.registrarEnvio(port.tipo(), resultado, Duration.ofNanos(System.nanoTime() - inicio));
            ResultadoEnvio definitivo = resultado;
            tx.executeWithoutResult(estado -> repository.findById(id).ifPresent(op -> aplicar(op, definitivo)));
        } catch (RuntimeException e) {
            // La operación queda EN_PROCESO y la recuperación la retomará.
            log.error("No se pudo registrar el resultado de la operación {}", id, e);
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }

    private void aplicar(IntegrationOperation op, ResultadoEnvio resultado) {
        if (op.getEstado() != com.bolivar.polizas.entity.enums.EstadoIntegracion.EN_PROCESO) {
            log.warn("Operación {} ya no está EN_PROCESO ({}); se ignora el resultado", op.getId(), op.getEstado());
            return;
        }
        Instant ahora = clock.instant();
        switch (resultado) {
            case ResultadoEnvio.Confirmado c -> {
                op.confirmar(ahora, c.status(), resultadoJson(c.status(), c.adaptador(), c.cuerpo()));
                auditar(op, "INTEGRACION_CONFIRMADA", ResultadoAuditoria.EXITO, null);
                metricas.registrarDesenlace("CONFIRMADA");
                log.info("Operación {} CONFIRMADA por {} en el intento {}", op.getId(), c.adaptador(), op.getIntentos());
            }
            case ResultadoEnvio.Rechazado r -> {
                op.rechazar(r.status(), r.detalle(), resultadoJson(r.status(), r.adaptador(), r.cuerpo()));
                auditar(op, "INTEGRACION_RECHAZADA", ResultadoAuditoria.RECHAZADO, r.detalle());
                metricas.registrarDesenlace("RECHAZADA");
                log.warn("Operación {} RECHAZADA por el CORE: {}", op.getId(), r.detalle());
            }
            case ResultadoEnvio.FalloTransitorio f -> reintentarOFallar(op, f, ahora);
            case ResultadoEnvio.Incierto i -> {
                if (config.reintentarResultadosInciertos()) {
                    reintentarOFallar(op, i, ahora);
                } else {
                    fallar(op, i.httpStatus(), "Resultado incierto que requiere revisión (no se reenvía para evitar "
                            + "duplicados): " + i.detalle());
                }
            }
        }
        sincronizacion.actualizar(op);
    }

    private void reintentarOFallar(IntegrationOperation op, ResultadoEnvio resultado, Instant ahora) {
        if (op.puedeReintentar()) {
            Instant proximo = ahora.plus(config.intervaloReintento());
            op.programarReintento(proximo, resultado.httpStatus(), resultado.detalle());
            metricas.registrarDesenlace("REINTENTO_PROGRAMADO");
            log.warn("Operación {} intento {}/{} sin confirmar ({}). Próximo intento: {}", op.getId(),
                    op.getIntentos(), op.getMaxIntentos(), resultado.detalle(), proximo);
        } else {
            fallar(op, resultado.httpStatus(), "Reintentos agotados (" + op.getIntentos() + "/" + op.getMaxIntentos()
                    + "): " + resultado.detalle());
        }
    }

    private void fallar(IntegrationOperation op, Integer status, String error) {
        op.marcarFallida(status, error);
        auditar(op, "INTEGRACION_FALLIDA", ResultadoAuditoria.ERROR, error);
        metricas.registrarDesenlace("FALLIDA");
        log.error("Operación {} FALLIDA: {}", op.getId(), error);
    }

    private void auditar(IntegrationOperation op, String accion, ResultadoAuditoria resultado, String error) {
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("operacionNegocio", op.getOperacionNegocio().name());
        detalle.put("accionRemota", op.getAccionRemota().name());
        detalle.put("intentos", op.getIntentos());
        detalle.put("adaptador", String.valueOf(op.getAdaptador()));
        if (op.getUltimoHttpStatus() != null) {
            detalle.put("httpStatus", op.getUltimoHttpStatus());
        }
        if (error != null) {
            detalle.put("error", error.length() > 300 ? error.substring(0, 300) : error);
        }
        auditoria.registrar(accion, op.getEntityType(), op.getEntityId(), resultado, op.getId(), detalle);
    }

    private String resultadoJson(int status, AdaptadorIntegracion adaptador, String cuerpo) {
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("httpStatus", status);
        resultado.put("adaptador", adaptador.name());
        resultado.put("simulado", adaptador == AdaptadorIntegracion.SIMULADO);
        resultado.put("respuesta", cuerpo);
        try {
            return objectMapper.writeValueAsString(resultado);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el resultado", e);
        }
    }

    private static void validarUmbral(PolizasProperties properties) {
        PolizasProperties.Integracion integracion = properties.integracion();
        if (integracion.adaptador() == AdaptadorIntegracion.WEBLOGIC && integracion.weblogic() != null) {
            Duration maximoIntento = integracion.weblogic().connectTimeout().plus(integracion.weblogic().readTimeout());
            if (integracion.procesador().umbralOperacionInterrumpida().compareTo(maximoIntento.multipliedBy(2)) <= 0) {
                throw new IllegalStateException("umbral-operacion-interrumpida debe superar el doble de "
                        + "connect-timeout + read-timeout (" + maximoIntento + ") para no tomar como interrumpida "
                        + "una operación aún en curso");
            }
        }
    }
}
