package com.bolivar.polizas.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class PeriodoIpcTest {

    @ParameterizedTest
    @CsvSource({
            "2026, 1,  ipc_2026_01, ipc_enero",
            "2026, 2,  ipc_2026_02, ipc_febrero",
            "2027, 9,  ipc_2027_09, ipc_septiembre",
            "2030, 12, ipc_2030_12, ipc_diciembre"
    })
    void generaLaClaveConPeriodoYLaClaveMensualHeredada(int anio, int mes, String conPeriodo, String mensual) {
        PeriodoIpc periodo = PeriodoIpc.de(anio, mes);

        assertThat(periodo.claveConPeriodo()).isEqualTo(conPeriodo);
        assertThat(periodo.claveMensualSinAnio()).isEqualTo(mensual);
    }

    @Test
    void ambasClavesCumplenElCheckDeLaTabla() {
        for (int mes = 1; mes <= 12; mes++) {
            PeriodoIpc periodo = PeriodoIpc.de(2026, mes);
            assertThat(periodo.claveConPeriodo()).matches("^[a-z][a-z0-9_]*$");
            assertThat(periodo.claveMensualSinAnio()).matches("^[a-z][a-z0-9_]*$");
        }
    }

    @Test
    void laReglaPorDefectoUsaElMesDeInicioDeLaNuevaVigencia() {
        LocalDate inicioNueva = LocalDate.of(2027, 1, 15);
        LocalDate fechaRenovacion = LocalDate.of(2026, 12, 20);

        assertThat(ReglaMesIpc.MES_INICIO_NUEVA_VIGENCIA.periodo(inicioNueva, fechaRenovacion).periodo())
                .isEqualTo(YearMonth.of(2027, 1));
        assertThat(ReglaMesIpc.MES_ANTERIOR_INICIO_NUEVA_VIGENCIA.periodo(inicioNueva, fechaRenovacion).periodo())
                .isEqualTo(YearMonth.of(2026, 12));
        assertThat(ReglaMesIpc.MES_FECHA_RENOVACION.periodo(inicioNueva, fechaRenovacion).periodo())
                .isEqualTo(YearMonth.of(2026, 12));
    }
}
