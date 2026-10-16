package com.bolivar.polizas.service;

import com.bolivar.polizas.entity.enums.OperacionNegocio;
import com.bolivar.polizas.exception.ValorInvalidoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaveIdempotenciaTest {

    @Test
    void laClaveDelClienteSeAislaPorOperacionYEntidad() {
        ClaveIdempotencia clave = ClaveIdempotencia.de(OperacionNegocio.CANCELAR_POLIZA, 7L, "abc-123");

        assertThat(clave.valor()).isEqualTo("CANCELAR_POLIZA:7:abc-123");
        assertThat(clave.provistaPorCliente()).isTrue();
        assertThat(ClaveIdempotencia.de(OperacionNegocio.CANCELAR_RIESGO, 7L, "abc-123").valor())
                .isNotEqualTo(clave.valor());
    }

    @Test
    void sinCabeceraGeneraUnaClaveUnicaNoReutilizable() {
        ClaveIdempotencia a = ClaveIdempotencia.de(OperacionNegocio.CANCELAR_POLIZA, 7L, null);
        ClaveIdempotencia b = ClaveIdempotencia.de(OperacionNegocio.CANCELAR_POLIZA, 7L, "  ");

        assertThat(a.provistaPorCliente()).isFalse();
        assertThat(a.valor()).startsWith("CANCELAR_POLIZA:7:").isNotEqualTo(b.valor());
        assertThat(a.valor().length()).isLessThanOrEqualTo(100);
    }

    @ParameterizedTest
    @ValueSource(strings = {"con espacio", "ñandú", "a/b", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})
    void rechazaClavesConFormatoInvalido(String clave) {
        assertThatThrownBy(() -> ClaveIdempotencia.de(OperacionNegocio.CANCELAR_POLIZA, 1L, clave))
                .isInstanceOf(ValorInvalidoException.class);
    }

    @Test
    void laClaveMasLargaCabeEnLaColumna() {
        String clave = ClaveIdempotencia.de(OperacionNegocio.MODIFICAR_RIESGO, Long.MAX_VALUE, "x".repeat(60)).valor();
        assertThat(clave.length()).isLessThanOrEqualTo(100);
    }
}
