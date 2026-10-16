package com.bolivar.polizas.integration.outbox;

import com.bolivar.polizas.entity.IntegrationOperation;
import com.bolivar.polizas.entity.enums.AccionRemota;
import com.bolivar.polizas.entity.enums.AdaptadorIntegracion;
import com.bolivar.polizas.entity.enums.EstadoSincronizacion;
import com.bolivar.polizas.entity.enums.OperacionNegocio;
import com.bolivar.polizas.entity.enums.TipoEntidad;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SincronizacionEntidadesTest {

    private static final Instant T0 = Instant.parse("2026-10-16T15:00:00Z");

    @Test
    void sinOperacionesNoRequiereSincronizacion() {
        assertThat(SincronizacionEntidades.agregado(List.of())).isEqualTo(EstadoSincronizacion.NO_REQUERIDA);
    }

    @Test
    void todasConfirmadasEsSincronizada() {
        assertThat(SincronizacionEntidades.agregado(List.of(confirmada(), confirmada())))
                .isEqualTo(EstadoSincronizacion.SINCRONIZADA);
    }

    @Test
    void unaPendienteMantieneElEstadoPendiente() {
        assertThat(SincronizacionEntidades.agregado(List.of(confirmada(), pendiente())))
                .isEqualTo(EstadoSincronizacion.PENDIENTE);
    }

    @Test
    void unaConfirmacionPosteriorNoOcultaUnaFallidaAnterior() {
        assertThat(SincronizacionEntidades.agregado(List.of(fallida(), confirmada(), pendiente())))
                .isEqualTo(EstadoSincronizacion.FALLIDA);
    }

    private static IntegrationOperation pendiente() {
        return new IntegrationOperation("k-" + UUID.randomUUID(), UUID.randomUUID(), TipoEntidad.POLIZA, 1L,
                OperacionNegocio.MODIFICAR_POLIZA, AccionRemota.MODIFICAR, null, LocalDate.of(2026, 10, 16), "{}", 4, T0);
    }

    private static IntegrationOperation confirmada() {
        IntegrationOperation op = pendiente();
        op.iniciarIntento(T0, AdaptadorIntegracion.SIMULADO);
        op.confirmar(T0, 200, "{}");
        return op;
    }

    private static IntegrationOperation fallida() {
        IntegrationOperation op = pendiente();
        op.iniciarIntento(T0, AdaptadorIntegracion.SIMULADO);
        op.marcarFallida(503, "x");
        return op;
    }
}
