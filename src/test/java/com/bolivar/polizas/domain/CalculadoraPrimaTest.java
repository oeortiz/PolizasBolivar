package com.bolivar.polizas.domain;

import com.bolivar.polizas.exception.ValorInvalidoException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculadoraPrimaTest {

    private final CalculadoraPrima calculadora = new CalculadoraPrima();

    @Nested
    class CalculoDePrima {

        @Test
        void ejemploDelEnunciado() {
            // Canon $1.500.000 × 12 meses = $18.000.000
            BigDecimal prima = calculadora.calcularPrima(new BigDecimal("1500000"), 12);

            assertThat(prima).isEqualByComparingTo("18000000");
            assertThat(prima.scale()).isEqualTo(2);
        }

        @ParameterizedTest
        @CsvSource({
                "1500000.00, 1,  1500000.00",
                "1234567.89, 6,  7407407.34",
                "0.01,       12, 0.12",
                "999999.99,  24, 23999999.76"
        })
        void multiplicaCanonPorMeses(String canon, int meses, String esperado) {
            assertThat(calculadora.calcularPrima(new BigDecimal(canon), meses))
                    .isEqualByComparingTo(esperado);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, -12})
        void rechazaMesesNoPositivos(int meses) {
            assertThatThrownBy(() -> calculadora.calcularPrima(new BigDecimal("1500000"), meses))
                    .isInstanceOf(ValorInvalidoException.class)
                    .hasMessageContaining("meses");
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "0.00", "-1"})
        void rechazaCanonNoPositivo(String canon) {
            assertThatThrownBy(() -> calculadora.calcularPrima(new BigDecimal(canon), 12))
                    .isInstanceOf(ValorInvalidoException.class)
                    .hasMessageContaining("mayor que cero");
        }

        @Test
        void rechazaCanonNulo() {
            assertThatThrownBy(() -> calculadora.calcularPrima(null, 12))
                    .isInstanceOf(ValorInvalidoException.class);
        }

        @Test
        void rechazaCanonConMasDeDosDecimalesEnLugarDeRedondearlo() {
            assertThatThrownBy(() -> calculadora.calcularPrima(new BigDecimal("1500000.005"), 12))
                    .isInstanceOf(ValorInvalidoException.class)
                    .hasMessageContaining("decimales");
        }

        @Test
        void aceptaCerosDecimalesSobrantes() {
            assertThat(calculadora.calcularPrima(new BigDecimal("1500000.5000"), 2))
                    .isEqualByComparingTo("3000001.00");
        }

        @Test
        void rechazaCanonPorEncimaDelMaximoDeLaColumna() {
            BigDecimal excesivo = PoliticaMonetaria.CANON_MAXIMO.add(new BigDecimal("0.01"));
            assertThatThrownBy(() -> calculadora.calcularPrima(excesivo, 1))
                    .isInstanceOf(ValorInvalidoException.class);
        }

        @Test
        void rechazaPrimaQueDesbordaLaColumna() {
            assertThatThrownBy(() -> calculadora.calcularPrima(PoliticaMonetaria.CANON_MAXIMO, 1000))
                    .isInstanceOf(ValorInvalidoException.class)
                    .hasMessageContaining("prima");
        }
    }

    @Nested
    class AplicacionDeIpc {

        @Test
        void ejemploDelEnunciado() {
            // Canon $1.500.000, IPC 5,60 % → canon $1.584.000; × 12 → prima $19.008.000
            BigDecimal canonNuevo = calculadora.aplicarIpc(new BigDecimal("1500000"), new BigDecimal("5.60"));
            BigDecimal primaNueva = calculadora.calcularPrima(canonNuevo, 12);

            assertThat(canonNuevo).isEqualByComparingTo("1584000");
            assertThat(canonNuevo.scale()).isEqualTo(2);
            assertThat(primaNueva).isEqualByComparingTo("19008000");
        }

        @Test
        void elIpcSeAplicaAlCanonYLuegoSeRecalculaLaPrima() {
            // Con redondeo, el orden importa: aplicar el IPC directamente a la prima da otro valor.
            BigDecimal canon = new BigDecimal("100.10");
            BigDecimal ipc = new BigDecimal("5");

            BigDecimal canonNuevo = calculadora.aplicarIpc(canon, ipc);          // 105.11
            BigDecimal primaCorrecta = calculadora.calcularPrima(canonNuevo, 12); // 1261.32
            BigDecimal primaIncorrecta = calculadora.aplicarIpc(
                    calculadora.calcularPrima(canon, 12), ipc);                    // 1201.20 → 1261.26

            assertThat(primaCorrecta).isEqualByComparingTo("1261.32");
            assertThat(primaIncorrecta).isEqualByComparingTo("1261.26");
        }

        @ParameterizedTest
        @CsvSource({
                // canon,   ipc,     esperado   (resultado exacto → redondeo HALF_UP a 2 decimales)
                "100.10,    5,       105.11",   // 105.105    → 105.11 (HALF_EVEN daría 105.10)
                "100.05,    5,       105.05",   // 105.0525   → 105.05
                "1234567.89, 5.6,    1303703.69", // 1303703.69184 → 1303703.69
                "1000000,   5.8,     1058000.00",
                "1000000,   0,       1000000.00",
                "1000000,   -1.25,   987500.00",
                "999.99,    3.3333,  1033.32"   // 1033.32266667 → 1033.32
        })
        void aplicaFormulaConRedondeoHalfUpUnicaVez(String canon, String ipc, String esperado) {
            assertThat(calculadora.aplicarIpc(new BigDecimal(canon), new BigDecimal(ipc)))
                    .isEqualByComparingTo(esperado);
        }

        @Test
        void rechazaIpcAusenteSinAsumirCero() {
            assertThatThrownBy(() -> calculadora.aplicarIpc(new BigDecimal("1500000"), null))
                    .isInstanceOf(ValorInvalidoException.class)
                    .hasMessageContaining("no se asume");
        }

        @ParameterizedTest
        @ValueSource(strings = {"-100", "-150", "1000", "5.12345"})
        void rechazaIpcFueraDeRangoOConDemasiadosDecimales(String ipc) {
            assertThatThrownBy(() -> calculadora.aplicarIpc(new BigDecimal("1500000"), new BigDecimal(ipc)))
                    .isInstanceOf(ValorInvalidoException.class);
        }

        @Test
        void rechazaResultadoQueDejaElCanonEnCero() {
            // 0.01 × (100 - 99.9999) / 100 = 0.000000... → 0.00
            assertThatThrownBy(() -> calculadora.aplicarIpc(new BigDecimal("0.01"), new BigDecimal("-99.9999")))
                    .isInstanceOf(ValorInvalidoException.class)
                    .hasMessageContaining("cero");
        }
    }

    @Nested
    class Consolidacion {

        @Test
        void sumaLasPrimas() {
            BigDecimal total = calculadora.consolidar(List.of(
                    new BigDecimal("18000000.00"), new BigDecimal("12000000.00"), new BigDecimal("0.50")));

            assertThat(total).isEqualByComparingTo("30000000.50");
            assertThat(total.scale()).isEqualTo(2);
        }

        @Test
        void coleccionVaciaConsolidaEnCero() {
            assertThat(calculadora.consolidar(List.of())).isEqualByComparingTo("0").hasScaleOf(2);
        }

        @Test
        void rechazaPrimasNulas() {
            assertThatThrownBy(() -> calculadora.consolidar(Arrays.asList(BigDecimal.ONE, null)))
                    .isInstanceOf(ValorInvalidoException.class);
            assertThatThrownBy(() -> calculadora.consolidar(null))
                    .isInstanceOf(ValorInvalidoException.class);
        }
    }
}
