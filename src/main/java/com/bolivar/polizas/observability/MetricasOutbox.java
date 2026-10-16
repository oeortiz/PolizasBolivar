package com.bolivar.polizas.observability;

import com.bolivar.polizas.entity.enums.EstadoIntegracion;
import com.bolivar.polizas.repository.IntegrationOperationRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/**
 * Gauge {@code polizas.integracion.operaciones{estado=...}}: operaciones del outbox por estado.
 * Permite alertar sobre operaciones pendientes acumuladas o fallidas que requieren revisión.
 * Si la base de datos no responde, el gauge informa NaN en lugar de fallar el scrape.
 */
@Component
public class MetricasOutbox implements MeterBinder {

    private final IntegrationOperationRepository repository;

    public MetricasOutbox(IntegrationOperationRepository repository) {
        this.repository = repository;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (EstadoIntegracion estado : EstadoIntegracion.values()) {
            Gauge.builder("polizas.integracion.operaciones", repository, r -> contar(r, estado))
                    .description("Operaciones de integración con el CORE por estado")
                    .tag("estado", estado.name())
                    .register(registry);
        }
    }

    private static double contar(IntegrationOperationRepository repository, EstadoIntegracion estado) {
        try {
            return repository.countByEstado(estado);
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }
}
