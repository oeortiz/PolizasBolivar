package com.bolivar.polizas.integration.mapping;

import com.bolivar.polizas.entity.enums.AccionRemota;
import com.bolivar.polizas.entity.enums.OperacionNegocio;
import com.bolivar.polizas.entity.enums.TipoEntidad;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class MapeadorOperacionCoreTest {

    private final MapeadorOperacionCore mapeador = new MapeadorOperacionCore();

    @Test
    void elContratoRemotoSoloAdmiteLasCuatroAcciones() {
        assertThat(Arrays.stream(AccionRemota.values()).map(Enum::name))
                .containsExactlyInAnyOrder("ANULAR", "SUSPENDER", "ACTIVAR", "MODIFICAR")
                .doesNotContain("RENOVAR", "CANCELAR");
    }

    @ParameterizedTest
    @CsvSource({
            "CANCELAR_POLIZA,  POLIZA, ANULAR",
            "CANCELAR_RIESGO,  RIESGO, ANULAR",
            "RENOVAR_POLIZA,   POLIZA, MODIFICAR",
            "MODIFICAR_POLIZA, POLIZA, MODIFICAR",
            "MODIFICAR_RIESGO, RIESGO, MODIFICAR",
            "AGREGAR_RIESGO,   POLIZA, MODIFICAR"
    })
    void traduceCadaOperacionDeNegocio(OperacionNegocio operacion, TipoEntidad entidad, AccionRemota accion) {
        assertThat(mapeador.mapear(operacion)).isEqualTo(new DestinoRemoto(entidad, accion));
    }

    @ParameterizedTest
    @EnumSource(OperacionNegocio.class)
    void todaOperacionDeNegocioTieneDestinoRemoto(OperacionNegocio operacion) {
        DestinoRemoto destino = mapeador.mapear(operacion);
        assertThat(destino.accion()).isNotNull();
        assertThat(destino.entityType()).isNotNull();
    }
}
