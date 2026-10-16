package com.bolivar.polizas.integration.weblogic;

import com.bolivar.polizas.entity.enums.AdaptadorIntegracion;
import com.bolivar.polizas.integration.port.ResultadoEnvio;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ClasificadorRespuestaCoreTest {

    @ParameterizedTest
    @CsvSource({
            "200, Confirmado", "201, Confirmado", "204, Confirmado",
            "202, Incierto",
            "400, Rechazado", "404, Rechazado", "409, Rechazado", "422, Rechazado",
            "401, FalloTransitorio", "408, FalloTransitorio", "429, FalloTransitorio", "503, FalloTransitorio",
            "500, Incierto", "502, Incierto", "504, Incierto", "302, Incierto"
    })
    void clasificaCadaEstadoHttp(int status, String esperado) {
        ResultadoEnvio resultado = ClasificadorRespuestaCore.clasificar(status, "{}");

        assertThat(resultado.getClass().getSimpleName()).isEqualTo(esperado);
        assertThat(resultado.httpStatus()).isEqualTo(status);
        assertThat(resultado.adaptador()).isEqualTo(AdaptadorIntegracion.WEBLOGIC);
    }
}
