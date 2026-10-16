package com.bolivar.polizas.observability;

import com.bolivar.polizas.entity.enums.AdaptadorIntegracion;
import com.bolivar.polizas.entity.enums.EstadoIntegracion;
import com.bolivar.polizas.integration.port.ResultadoEnvio;
import com.bolivar.polizas.repository.IntegrationOperationRepository;
import com.bolivar.polizas.support.PuertoCoreProgramable;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ObservabilidadTest {

    @Test
    void registraLatenciaPorResultadoYDesenlaces() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MetricasIntegracion metricas = new MetricasIntegracion(registry);

        metricas.registrarEnvio(AdaptadorIntegracion.SIMULADO,
                new ResultadoEnvio.Confirmado(200, "{}", AdaptadorIntegracion.SIMULADO), Duration.ofMillis(120));
        metricas.registrarDesenlace("CONFIRMADA");
        metricas.registrarDesenlace("CONFIRMADA");

        assertThat(registry.get("polizas.integracion.envio")
                .tag("adaptador", "SIMULADO").tag("resultado", "Confirmado").timer().count()).isEqualTo(1);
        assertThat(registry.get("polizas.integracion.desenlaces")
                .tag("desenlace", "CONFIRMADA").counter().count()).isEqualTo(2.0);
    }

    @Test
    void publicaOperacionesPorEstado() {
        IntegrationOperationRepository repository = mock(IntegrationOperationRepository.class);
        when(repository.countByEstado(EstadoIntegracion.FALLIDA)).thenReturn(3L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new MetricasOutbox(repository).bindTo(registry);

        assertThat(registry.get("polizas.integracion.operaciones").tag("estado", "FALLIDA").gauge().value())
                .isEqualTo(3.0);
    }

    @Test
    void elHealthCheckAdvierteQueElAdaptadorEsSimulado() {
        IntegrationOperationRepository repository = mock(IntegrationOperationRepository.class);
        when(repository.countByEstado(EstadoIntegracion.PENDIENTE)).thenReturn(2L);
        when(repository.countByEstado(EstadoIntegracion.FALLIDA)).thenReturn(1L);

        Health health = new IntegracionCoreHealthIndicator(new PuertoCoreProgramable(), repository).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("adaptador", "SIMULADO")
                .containsEntry("coreReal", false)
                .containsEntry("operacionesPendientes", 2L)
                .containsEntry("operacionesFallidas", 1L)
                .containsKey("advertencia");
    }

    @Test
    void elHealthCheckReportaDownSiNoPuedeConsultarElOutbox() {
        IntegrationOperationRepository repository = mock(IntegrationOperationRepository.class);
        when(repository.countByEstado(any())).thenThrow(new IllegalStateException("BD caída"));

        Health health = new IntegracionCoreHealthIndicator(new PuertoCoreProgramable(), repository).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }
}
