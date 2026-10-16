package com.bolivar.polizas.observability;

import com.bolivar.polizas.entity.enums.AdaptadorIntegracion;
import com.bolivar.polizas.integration.port.ResultadoEnvio;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.Objects;

/**
 * Métricas de la integración con el CORE (expuestas en /actuator/prometheus):
 * <ul>
 *   <li>{@code polizas.integracion.envio} (timer): latencia de cada intento, por adaptador y resultado.</li>
 *   <li>{@code polizas.integracion.desenlaces} (contador): CONFIRMADA, RECHAZADA, FALLIDA, REINTENTO_PROGRAMADO.</li>
 * </ul>
 * Las métricas HTTP de la API ({@code http.server.requests}: latencia y errores por endpoint y
 * estado) las provee Spring Boot Actuator. Las operaciones por estado las publica {@link MetricasOutbox}.
 */
public class MetricasIntegracion {

    private final MeterRegistry registry;

    public MetricasIntegracion(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry);
    }

    public void registrarEnvio(AdaptadorIntegracion adaptador, ResultadoEnvio resultado, Duration duracion) {
        Timer.builder("polizas.integracion.envio")
                .description("Latencia de cada intento de envío de una mutación al CORE")
                .tag("adaptador", adaptador.name())
                .tag("resultado", resultado.getClass().getSimpleName())
                .register(registry)
                .record(duracion);
    }

    public void registrarDesenlace(String desenlace) {
        Counter.builder("polizas.integracion.desenlaces")
                .description("Desenlaces del procesamiento de operaciones de integración")
                .tag("desenlace", desenlace)
                .register(registry)
                .increment();
    }
}
