package com.bolivar.polizas.domain;

import com.bolivar.polizas.exception.ValorInvalidoException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VigenciaTest {

    @Test
    void calculaFechaFinSumandoLaDuracion() {
        Vigencia vigencia = Vigencia.desde(LocalDate.of(2026, 1, 15), 12);

        assertThat(vigencia.fechaFin()).isEqualTo(LocalDate.of(2027, 1, 15));
        assertThat(vigencia.duracionMeses()).isEqualTo(12);
    }

    @Test
    void ajustaAlUltimoDiaDelMesCuandoElDiaNoExiste() {
        assertThat(Vigencia.desde(LocalDate.of(2026, 1, 31), 1).fechaFin())
                .isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void laSiguienteVigenciaEmpiezaDondeTerminaLaActualConLaMismaDuracion() {
        Vigencia actual = Vigencia.desde(LocalDate.of(2026, 1, 1), 12);

        Vigencia siguiente = actual.siguiente();

        assertThat(siguiente.fechaInicio()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(siguiente.fechaFin()).isEqualTo(LocalDate.of(2028, 1, 1));
        assertThat(siguiente.duracionMeses()).isEqualTo(12);
    }

    @Test
    void rechazaDuracionNoPositivaYFechasIncoherentes() {
        assertThatThrownBy(() -> Vigencia.desde(LocalDate.of(2026, 1, 1), 0))
                .isInstanceOf(ValorInvalidoException.class);
        assertThatThrownBy(() -> Vigencia.desde(null, 12))
                .isInstanceOf(ValorInvalidoException.class);
        assertThatThrownBy(() -> new Vigencia(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), 12))
                .isInstanceOf(ValorInvalidoException.class);
    }
}
