package com.bolivar.polizas.entity;

import com.bolivar.polizas.domain.CalculadoraPrima;
import com.bolivar.polizas.domain.Vigencia;
import com.bolivar.polizas.entity.enums.EstadoPoliza;
import com.bolivar.polizas.entity.enums.EstadoRiesgo;
import com.bolivar.polizas.entity.enums.TipoDocumento;
import com.bolivar.polizas.exception.CodigoError;
import com.bolivar.polizas.exception.ReglaNegocioException;
import com.bolivar.polizas.exception.ValorInvalidoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reglas de renovación del agregado: IPC sobre canon, prima recalculada, vigencia y estado. */
class PolizaRenovacionTest {

    private static final CalculadoraPrima CALCULADORA = new CalculadoraPrima();
    private static final Vigencia VIGENCIA = Vigencia.desde(LocalDate.of(2026, 1, 1), 12);

    @Test
    void renuevaIndividualConElEjemploDelEnunciado() {
        Poliza poliza = Poliza.crearIndividual(riesgo("Calle 1", "1500000"), VIGENCIA, CALCULADORA);

        ResultadoRenovacion r = poliza.renovar(new BigDecimal("5.60"), CALCULADORA);

        // Canon 1.500.000 × 1,056 = 1.584.000; prima 1.584.000 × 12 = 19.008.000
        assertThat(poliza.getCanonMensual()).isEqualByComparingTo("1584000");
        assertThat(poliza.getPrima()).isEqualByComparingTo("19008000");
        assertThat(poliza.getRiesgos().getFirst().getPrima()).isEqualByComparingTo("19008000");
        assertThat(poliza.getEstado()).isEqualTo(EstadoPoliza.RENOVADA);
        assertThat(poliza.getFechaInicioVigencia()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(poliza.getFechaFinVigencia()).isEqualTo(LocalDate.of(2028, 1, 1));
        assertThat(poliza.getDuracionMeses()).isEqualTo(12);

        assertThat(r.anterior().canonMensual()).isEqualByComparingTo("1500000");
        assertThat(r.anterior().prima()).isEqualByComparingTo("18000000");
        assertThat(r.anterior().estado()).isEqualTo(EstadoPoliza.ACTIVA);
        assertThat(r.anterior().fechaInicioVigencia()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(r.nueva().canonMensual()).isEqualByComparingTo("1584000");
        assertThat(r.nueva().prima()).isEqualByComparingTo("19008000");
        assertThat(r.nueva().estado()).isEqualTo(EstadoPoliza.RENOVADA);
        assertThat(r.riesgos()).singleElement().satisfies(d -> {
            assertThat(d.canonAnterior()).isEqualByComparingTo("1500000");
            assertThat(d.canonNuevo()).isEqualByComparingTo("1584000");
            assertThat(d.primaAnterior()).isEqualByComparingTo("18000000");
            assertThat(d.primaNueva()).isEqualByComparingTo("19008000");
        });
    }

    @Test
    void enColectivaRenuevaSoloLosRiesgosActivosYReconsolida() {
        Poliza poliza = Poliza.crearColectiva(
                new Participante(TipoDocumento.NIT, "900123456", "Inmobiliaria"), VIGENCIA,
                List.of(riesgo("A1", "1500000"), riesgo("A2", "1000000"), riesgo("A3", "100.10")), CALCULADORA);
        Riesgo cancelado = poliza.getRiesgos().get(1);
        poliza.cancelarRiesgo(cancelado, Instant.parse("2026-06-01T00:00:00Z"), null, CALCULADORA);

        ResultadoRenovacion r = poliza.renovar(new BigDecimal("5"), CALCULADORA);

        // 1.500.000 → 1.575.000 (prima 18.900.000); 100,10 → 105,11 (HALF_UP; prima 1.261,32)
        assertThat(poliza.getRiesgos().get(0).getCanonMensual()).isEqualByComparingTo("1575000");
        assertThat(poliza.getRiesgos().get(2).getCanonMensual()).isEqualByComparingTo("105.11");
        assertThat(poliza.getPrima()).isEqualByComparingTo("18901261.32");
        assertThat(poliza.getCanonMensual()).isEqualByComparingTo("1575105.11");
        // El riesgo cancelado no se renueva ni suma
        assertThat(cancelado.getEstado()).isEqualTo(EstadoRiesgo.CANCELADO);
        assertThat(cancelado.getCanonMensual()).isEqualByComparingTo("1000000");
        assertThat(r.riesgos()).hasSize(2);
        assertThat(r.anterior().prima()).isEqualByComparingTo("18001201.20");
    }

    @Test
    void unaSegundaRenovacionParteDeLosValoresYaRenovados() {
        Poliza poliza = Poliza.crearIndividual(riesgo("Calle 1", "1500000"), VIGENCIA, CALCULADORA);
        poliza.renovar(new BigDecimal("5.60"), CALCULADORA);

        ResultadoRenovacion r = poliza.renovar(new BigDecimal("5.80"), CALCULADORA);

        assertThat(r.anterior().estado()).isEqualTo(EstadoPoliza.RENOVADA);
        assertThat(r.anterior().canonMensual()).isEqualByComparingTo("1584000");
        // 1.584.000 × 1,058 = 1.675.872
        assertThat(poliza.getCanonMensual()).isEqualByComparingTo("1675872");
        assertThat(poliza.getPrima()).isEqualByComparingTo("20110464");
        assertThat(poliza.getFechaInicioVigencia()).isEqualTo(LocalDate.of(2028, 1, 1));
    }

    @Test
    void noRenuevaUnaPolizaCancelada() {
        Poliza poliza = Poliza.crearIndividual(riesgo("Calle 1", "1500000"), VIGENCIA, CALCULADORA);
        poliza.cancelar(Instant.parse("2026-06-01T00:00:00Z"), "motivo");

        assertThatThrownBy(() -> poliza.renovar(new BigDecimal("5.60"), CALCULADORA))
                .isInstanceOf(ReglaNegocioException.class)
                .hasFieldOrPropertyWithValue("codigo", CodigoError.POLIZA_CANCELADA);
    }

    @Test
    void sinIpcNoRenuevaYNoModificaNada() {
        Poliza poliza = Poliza.crearIndividual(riesgo("Calle 1", "1500000"), VIGENCIA, CALCULADORA);

        assertThatThrownBy(() -> poliza.renovar(null, CALCULADORA)).isInstanceOf(ValorInvalidoException.class);

        assertThat(poliza.getEstado()).isEqualTo(EstadoPoliza.ACTIVA);
        assertThat(poliza.getCanonMensual()).isEqualByComparingTo("1500000");
        assertThat(poliza.getFechaInicioVigencia()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void siUnRiesgoDesbordaLosLimitesElAgregadoQuedaIntacto() {
        Poliza poliza = Poliza.crearColectiva(
                new Participante(TipoDocumento.NIT, "900123456", "Inmobiliaria"), VIGENCIA,
                List.of(riesgo("A1", "1500000"), riesgo("A2", "9000000000000")), CALCULADORA);

        assertThatThrownBy(() -> poliza.renovar(new BigDecimal("20"), CALCULADORA))
                .isInstanceOf(ValorInvalidoException.class);

        assertThat(poliza.getRiesgos().getFirst().getCanonMensual()).isEqualByComparingTo("1500000");
        assertThat(poliza.getEstado()).isEqualTo(EstadoPoliza.ACTIVA);
    }

    private static DatosRiesgo riesgo(String direccion, String canon) {
        return new DatosRiesgo(direccion,
                new Participante(TipoDocumento.CC, "1020304050", "Carlos Arrendatario"),
                new Participante(TipoDocumento.CC, "79888777", "Ana Arrendadora"),
                new BigDecimal(canon));
    }
}
