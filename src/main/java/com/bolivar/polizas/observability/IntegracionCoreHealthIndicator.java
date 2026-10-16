package com.bolivar.polizas.observability;

import com.bolivar.polizas.entity.enums.AdaptadorIntegracion;
import com.bolivar.polizas.entity.enums.EstadoIntegracion;
import com.bolivar.polizas.integration.port.CoreMutationPort;
import com.bolivar.polizas.repository.IntegrationOperationRepository;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Componente {@code integracionCore} de /actuator/health. Informa qué adaptador está activo
 * (y advierte si es el simulado) y el volumen de operaciones pendientes y fallidas.
 *
 * <p>No sondea WebLogic: el contrato no define un endpoint de salud y una mutación de prueba
 * no es aceptable. Las operaciones fallidas no marcan la aplicación como DOWN (la API sigue
 * atendiendo); se vigilan con la métrica {@code polizas.integracion.operaciones}.</p>
 */
@Component("integracionCore")
public class IntegracionCoreHealthIndicator implements HealthIndicator {

    private final CoreMutationPort port;
    private final IntegrationOperationRepository repository;

    public IntegracionCoreHealthIndicator(CoreMutationPort port, IntegrationOperationRepository repository) {
        this.port = port;
        this.repository = repository;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up()
                .withDetail("adaptador", port.tipo().name())
                .withDetail("coreReal", port.tipo() == AdaptadorIntegracion.WEBLOGIC);
        if (port.tipo() == AdaptadorIntegracion.SIMULADO) {
            builder.withDetail("advertencia", "Adaptador SIMULADO: ninguna mutación se envía al CORE real");
        }
        try {
            return builder
                    .withDetail("operacionesPendientes", repository.countByEstado(EstadoIntegracion.PENDIENTE)
                            + repository.countByEstado(EstadoIntegracion.EN_PROCESO))
                    .withDetail("operacionesFallidas", repository.countByEstado(EstadoIntegracion.FALLIDA))
                    .withDetail("operacionesRechazadas", repository.countByEstado(EstadoIntegracion.RECHAZADA))
                    .build();
        } catch (RuntimeException e) {
            return Health.down().withDetail("adaptador", port.tipo().name())
                    .withDetail("error", "No se pudo consultar el outbox: " + e.getClass().getSimpleName())
                    .build();
        }
    }
}
