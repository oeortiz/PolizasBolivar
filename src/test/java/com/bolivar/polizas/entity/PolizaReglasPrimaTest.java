package com.bolivar.polizas.entity;

import com.bolivar.polizas.domain.CalculadoraPrima;
import com.bolivar.polizas.domain.Vigencia;
import com.bolivar.polizas.entity.enums.EstadoRiesgo;
import com.bolivar.polizas.entity.enums.TipoDocumento;
import com.bolivar.polizas.entity.enums.TipoPoliza;
import com.bolivar.polizas.exception.CodigoError;
import com.bolivar.polizas.exception.ReglaNegocioException;
import com.bolivar.polizas.exception.ValorInvalidoException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reglas de canon, prima y consolidación aplicadas por el agregado Póliza (sin base de datos). */
class PolizaReglasPrimaTest {

    private static final CalculadoraPrima CALCULADORA = new CalculadoraPrima();
    private static final Vigencia VIGENCIA_12M = Vigencia.desde(LocalDate.of(2026, 1, 1), 12);
    private static final Instant AHORA = Instant.parse("2026-03-01T15:00:00Z");

    @Nested
    class Individual {

        @Test
        void calculaLaPrimaDelUnicoRiesgoYLaReflejaEnLaPolizaSinDobleConteo() {
            Poliza poliza = Poliza.crearIndividual(riesgo("Calle 1", "1500000"), VIGENCIA_12M, CALCULADORA);

            Riesgo unico = poliza.getRiesgos().getFirst();
            assertThat(poliza.getTipo()).isEqualTo(TipoPoliza.INDIVIDUAL);
            assertThat(poliza.getRiesgos()).hasSize(1);
            assertThat(unico.getPrima()).isEqualByComparingTo("18000000");
            assertThat(poliza.getCanonMensual()).isEqualByComparingTo(unico.getCanonMensual());
            // La prima de la póliza ES la del riesgo, no la suma de ambas (36.000.000).
            assertThat(poliza.getPrima()).isEqualByComparingTo("18000000");
        }

        @Test
        void elTomadorEsElArrendatario() {
            DatosRiesgo datos = riesgo("Calle 1", "1500000");
            Poliza poliza = Poliza.crearIndividual(datos, VIGENCIA_12M, CALCULADORA);

            assertThat(poliza.getTomador()).isEqualTo(datos.arrendatario());
            assertThat(poliza.getRiesgos().getFirst().getArrendador()).isEqualTo(datos.arrendador());
        }

        @Test
        void rechazaUnSegundoRiesgo() {
            Poliza poliza = Poliza.crearIndividual(riesgo("Calle 1", "1500000"), VIGENCIA_12M, CALCULADORA);

            assertThatThrownBy(() -> poliza.agregarRiesgo(riesgo("Calle 2", "900000"), CALCULADORA))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasFieldOrPropertyWithValue("codigo", CodigoError.RIESGO_ADICIONAL_EN_INDIVIDUAL);
            assertThat(poliza.getRiesgos()).hasSize(1);
            assertThat(poliza.getPrima()).isEqualByComparingTo("18000000");
        }

        @Test
        void suRiesgoNoSeCancelaPorSeparado() {
            Poliza poliza = Poliza.crearIndividual(riesgo("Calle 1", "1500000"), VIGENCIA_12M, CALCULADORA);
            Riesgo unico = poliza.getRiesgos().getFirst();

            assertThatThrownBy(() -> poliza.cancelarRiesgo(unico, AHORA, "motivo", CALCULADORA))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasFieldOrPropertyWithValue("codigo", CodigoError.RIESGO_INDIVIDUAL_NO_CANCELABLE);
        }

        @Test
        void rechazaCanonInvalidoSinCrearLaPoliza() {
            assertThatThrownBy(() -> Poliza.crearIndividual(riesgo("Calle 1", "0"), VIGENCIA_12M, CALCULADORA))
                    .isInstanceOf(ValorInvalidoException.class);
        }
    }

    @Nested
    class Colectiva {

        @Test
        void consolidaLasPrimasDeTodosLosRiesgos() {
            Poliza poliza = colectivaConDosRiesgos();

            assertThat(poliza.getRiesgos()).extracting(Riesgo::getPrima)
                    .usingElementComparator(BigDecimal::compareTo)
                    .containsExactly(new BigDecimal("18000000"), new BigDecimal("12000000"));
            assertThat(poliza.getPrima()).isEqualByComparingTo("30000000");
            assertThat(poliza.getCanonMensual()).isEqualByComparingTo("2500000");
        }

        @Test
        void agregarUnRiesgoUsaLaVigenciaDeLaPolizaYActualizaLaConsolidacion() {
            Poliza poliza = colectivaConDosRiesgos();

            Riesgo nuevo = poliza.agregarRiesgo(riesgo("Apto 103", "800000"), CALCULADORA);

            assertThat(nuevo.getPrima()).isEqualByComparingTo("9600000");
            assertThat(poliza.getPrima()).isEqualByComparingTo("39600000");
            assertThat(poliza.getCanonMensual()).isEqualByComparingTo("3300000");
        }

        @Test
        void cancelarUnRiesgoLoExcluyeDeLaConsolidacionYConservaSusValores() {
            Poliza poliza = colectivaConDosRiesgos();
            Riesgo cancelado = poliza.getRiesgos().getFirst();

            poliza.cancelarRiesgo(cancelado, AHORA, "Fin del contrato de arriendo", CALCULADORA);

            assertThat(cancelado.getEstado()).isEqualTo(EstadoRiesgo.CANCELADO);
            assertThat(cancelado.getFechaCancelacion()).isEqualTo(AHORA);
            assertThat(cancelado.getPrima()).isEqualByComparingTo("18000000"); // historial intacto
            assertThat(poliza.getRiesgos()).hasSize(2);                        // sin borrado
            assertThat(poliza.riesgosActivos()).hasSize(1);
            assertThat(poliza.getPrima()).isEqualByComparingTo("12000000");
            assertThat(poliza.getCanonMensual()).isEqualByComparingTo("1000000");
        }

        @Test
        void noPermiteCancelarDosVecesElMismoRiesgo() {
            Poliza poliza = colectivaConDosRiesgos();
            poliza.agregarRiesgo(riesgo("Apto 103", "800000"), CALCULADORA);
            Riesgo riesgo = poliza.getRiesgos().getFirst();
            poliza.cancelarRiesgo(riesgo, AHORA, null, CALCULADORA);

            assertThatThrownBy(() -> poliza.cancelarRiesgo(riesgo, AHORA, null, CALCULADORA))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasFieldOrPropertyWithValue("codigo", CodigoError.RIESGO_YA_CANCELADO);
            assertThat(poliza.getPrima()).isEqualByComparingTo("21600000");
        }

        @Test
        void noPermiteCancelarElUltimoRiesgoActivo() {
            Poliza poliza = colectivaConDosRiesgos();
            poliza.cancelarRiesgo(poliza.getRiesgos().getFirst(), AHORA, null, CALCULADORA);
            Riesgo ultimo = poliza.riesgosActivos().getFirst();

            assertThatThrownBy(() -> poliza.cancelarRiesgo(ultimo, AHORA, null, CALCULADORA))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasFieldOrPropertyWithValue("codigo", CodigoError.ULTIMO_RIESGO_ACTIVO);
        }

        @Test
        void rechazaRiesgosDeOtraPoliza() {
            Poliza poliza = colectivaConDosRiesgos();
            Riesgo ajeno = colectivaConDosRiesgos().getRiesgos().getFirst();

            assertThatThrownBy(() -> poliza.cancelarRiesgo(ajeno, AHORA, null, CALCULADORA))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasFieldOrPropertyWithValue("codigo", CodigoError.RIESGO_NO_PERTENECE_A_POLIZA);
        }

        @Test
        void requiereAlMenosUnRiesgo() {
            assertThatThrownBy(() -> Poliza.crearColectiva(tomadorInmobiliaria(), VIGENCIA_12M, List.of(), CALCULADORA))
                    .isInstanceOf(ValorInvalidoException.class);
        }

        @Test
        void laPrimaDeCadaRiesgoUsaLaDuracionDeLaPoliza() {
            Vigencia seisMeses = Vigencia.desde(LocalDate.of(2026, 1, 1), 6);
            Poliza poliza = Poliza.crearColectiva(tomadorInmobiliaria(), seisMeses,
                    List.of(riesgo("Apto 101", "1500000")), CALCULADORA);

            assertThat(poliza.getRiesgos().getFirst().getPrima()).isEqualByComparingTo("9000000");
            assertThat(poliza.getPrima()).isEqualByComparingTo("9000000");
        }
    }

    @Test
    void recalcularPrimasEsIdempotente() {
        Poliza poliza = colectivaConDosRiesgos();

        poliza.recalcularPrimas(CALCULADORA);
        poliza.recalcularPrimas(CALCULADORA);

        assertThat(poliza.getPrima()).isEqualByComparingTo("30000000");
    }

    private static Poliza colectivaConDosRiesgos() {
        return Poliza.crearColectiva(tomadorInmobiliaria(), VIGENCIA_12M,
                List.of(riesgo("Apto 101", "1500000"), riesgo("Apto 102", "1000000")), CALCULADORA);
    }

    private static Participante tomadorInmobiliaria() {
        return new Participante(TipoDocumento.NIT, "900123456", "Inmobiliaria Ejemplo SAS");
    }

    private static DatosRiesgo riesgo(String direccion, String canon) {
        return new DatosRiesgo(direccion,
                new Participante(TipoDocumento.CC, "1020304050", "Carlos Arrendatario"),
                new Participante(TipoDocumento.CC, "79888777", "Ana Arrendadora"),
                new BigDecimal(canon));
    }
}
