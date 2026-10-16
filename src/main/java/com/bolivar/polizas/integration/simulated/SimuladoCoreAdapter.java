package com.bolivar.polizas.integration.simulated;

import com.bolivar.polizas.config.PolizasProperties;
import com.bolivar.polizas.entity.enums.AdaptadorIntegracion;
import com.bolivar.polizas.integration.port.CoreMutationPort;
import com.bolivar.polizas.integration.port.ResultadoEnvio;
import com.bolivar.polizas.integration.port.SolicitudCore;
import com.bolivar.polizas.integration.weblogic.ClasificadorRespuestaCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Adaptador simulado para desarrollo y pruebas, cuando no hay endpoint, certificados ni
 * credenciales reales.
 *
 * <p><b>Ninguna mutación llega al CORE real.</b> Todos los resultados se marcan con
 * {@link AdaptadorIntegracion#SIMULADO}, que queda registrado en la operación; la API y los
 * logs nunca presentan una confirmación simulada como si viniera del CORE.</p>
 *
 * <p>Los estados HTTP simulados se clasifican con las mismas reglas que el adaptador real, de
 * modo que la política de reintentos se ejercita de forma idéntica.</p>
 */
public class SimuladoCoreAdapter implements CoreMutationPort {

    private static final Logger log = LoggerFactory.getLogger(SimuladoCoreAdapter.class);
    private static final AdaptadorIntegracion ADAPTADOR = AdaptadorIntegracion.SIMULADO;

    private final PolizasProperties.Simulado configuracion;
    private final Map<UUID, AtomicInteger> intentosPorMutacion = new ConcurrentHashMap<>();

    public SimuladoCoreAdapter(PolizasProperties.Simulado configuracion) {
        this.configuracion = Objects.requireNonNull(configuracion);
    }

    @Override
    public AdaptadorIntegracion tipo() {
        return ADAPTADOR;
    }

    @Override
    public ResultadoEnvio enviar(SolicitudCore solicitud) {
        esperar(configuracion.latencia());
        int intento = intentosPorMutacion.computeIfAbsent(solicitud.idempotencyKey(), k -> new AtomicInteger())
                .incrementAndGet();
        ResultadoEnvio resultado = switch (configuracion.modo()) {
            case EXITO -> confirmado(solicitud);
            case ERROR_HTTP -> desdeEstado(configuracion.httpStatus());
            case TIMEOUT -> new ResultadoEnvio.Incierto(null, "[SIMULADO] Timeout esperando respuesta del CORE", ADAPTADOR);
            case FALLA_Y_RECUPERA -> intento <= configuracion.fallosAntesDeExito()
                    ? desdeEstado(configuracion.httpStatus())
                    : confirmado(solicitud);
        };
        log.info("[SIMULADO] Mutación {} intento {} ({} {}) → {}", solicitud.idempotencyKey(), intento,
                solicitud.request().mutation().action(), solicitud.request().mutation().entityType(),
                resultado.getClass().getSimpleName());
        return resultado;
    }

    /** Intentos recibidos por una mutación (útil en pruebas). */
    public int intentosRecibidos(UUID idempotencyKey) {
        AtomicInteger contador = intentosPorMutacion.get(idempotencyKey);
        return contador == null ? 0 : contador.get();
    }

    private static ResultadoEnvio confirmado(SolicitudCore solicitud) {
        String cuerpo = "{\"simulado\":true,\"mensaje\":\"Confirmación simulada: el CORE real no fue invocado\","
                + "\"idempotencyKey\":\"" + solicitud.idempotencyKey() + "\"}";
        return new ResultadoEnvio.Confirmado(200, cuerpo, ADAPTADOR);
    }

    private static ResultadoEnvio desdeEstado(int status) {
        ResultadoEnvio real = ClasificadorRespuestaCore.clasificar(status, "{\"simulado\":true}");
        String detalle = "[SIMULADO] " + real.detalle();
        return switch (real) {
            case ResultadoEnvio.Confirmado c -> new ResultadoEnvio.Confirmado(c.status(), c.cuerpo(), ADAPTADOR);
            case ResultadoEnvio.Rechazado r -> new ResultadoEnvio.Rechazado(r.status(), r.cuerpo(), ADAPTADOR);
            case ResultadoEnvio.FalloTransitorio f -> new ResultadoEnvio.FalloTransitorio(f.httpStatus(), detalle, ADAPTADOR);
            case ResultadoEnvio.Incierto i -> new ResultadoEnvio.Incierto(i.httpStatus(), detalle, ADAPTADOR);
        };
    }

    private static void esperar(Duration latencia) {
        if (latencia == null || latencia.isZero() || latencia.isNegative()) {
            return;
        }
        try {
            Thread.sleep(latencia.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
