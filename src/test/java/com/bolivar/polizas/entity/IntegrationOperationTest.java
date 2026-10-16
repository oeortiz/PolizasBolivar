package com.bolivar.polizas.entity;

import com.bolivar.polizas.entity.enums.AccionRemota;
import com.bolivar.polizas.entity.enums.AdaptadorIntegracion;
import com.bolivar.polizas.entity.enums.EstadoIntegracion;
import com.bolivar.polizas.entity.enums.OperacionNegocio;
import com.bolivar.polizas.entity.enums.TipoEntidad;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntegrationOperationTest {

    private static final Instant T0 = Instant.parse("2026-10-16T15:00:00Z");

    @Test
    void cicloConfirmado() {
        IntegrationOperation op = nueva(4);

        op.iniciarIntento(T0, AdaptadorIntegracion.SIMULADO);
        op.confirmar(T0.plusSeconds(1), 200, "{\"ok\":true}");

        assertThat(op.getEstado()).isEqualTo(EstadoIntegracion.CONFIRMADA);
        assertThat(op.getIntentos()).isEqualTo(1);
        assertThat(op.getConfirmadaAt()).isEqualTo(T0.plusSeconds(1));
        assertThat(op.getAdaptador()).isEqualTo(AdaptadorIntegracion.SIMULADO);
    }

    @Test
    void consumeUnIntentoPorEnvioYNoSuperaElMaximo() {
        IntegrationOperation op = nueva(2);

        op.iniciarIntento(T0, AdaptadorIntegracion.SIMULADO);
        op.programarReintento(T0.plusSeconds(60), 503, "no disponible");
        op.iniciarIntento(T0.plusSeconds(60), AdaptadorIntegracion.SIMULADO);

        assertThat(op.getIntentos()).isEqualTo(2);
        assertThat(op.puedeReintentar()).isFalse();
        assertThatThrownBy(() -> op.programarReintento(T0.plusSeconds(120), 503, "x"))
                .isInstanceOf(IllegalStateException.class);
        op.marcarFallida(503, "agotado");
        assertThat(op.getEstado()).isEqualTo(EstadoIntegracion.FALLIDA);
        assertThat(op.getProximoIntentoAt()).isNull();
    }

    @Test
    void noSePuedeConfirmarSinUnIntentoEnCurso() {
        IntegrationOperation op = nueva(4);

        assertThatThrownBy(() -> op.confirmar(T0, 200, "{}")).isInstanceOf(IllegalStateException.class);
        op.iniciarIntento(T0, AdaptadorIntegracion.SIMULADO);
        op.confirmar(T0, 200, "{}");
        assertThatThrownBy(() -> op.confirmar(T0, 200, "{}")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> op.iniciarIntento(T0, AdaptadorIntegracion.SIMULADO))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void soloUnaFallidaPuedeReabrirseYRecibeNuevosIntentos() {
        IntegrationOperation op = nueva(1);
        op.iniciarIntento(T0, AdaptadorIntegracion.SIMULADO);
        assertThatThrownBy(() -> op.reabrir(T0)).isInstanceOf(IllegalStateException.class);
        op.marcarFallida(null, "timeout");

        op.reabrir(T0.plusSeconds(600));

        assertThat(op.getEstado()).isEqualTo(EstadoIntegracion.PENDIENTE);
        assertThat(op.getIntentos()).isZero();
        assertThat(op.getProximoIntentoAt()).isEqualTo(T0.plusSeconds(600));
        assertThat(op.getUltimoError()).isEqualTo("timeout");
    }

    @Test
    void recortaErroresLargosAlTamanoDeLaColumna() {
        IntegrationOperation op = nueva(4);
        op.iniciarIntento(T0, AdaptadorIntegracion.SIMULADO);

        op.marcarFallida(500, "x".repeat(5000));

        assertThat(op.getUltimoError()).hasSize(2000);
    }

    private static IntegrationOperation nueva(int max) {
        return new IntegrationOperation("k-" + UUID.randomUUID(), UUID.randomUUID(), TipoEntidad.POLIZA, 1L,
                OperacionNegocio.CANCELAR_POLIZA, AccionRemota.ANULAR, null, LocalDate.of(2026, 10, 16), "{}", max, T0);
    }
}
