package com.bolivar.polizas.controller;

import com.bolivar.polizas.service.CoreMockService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mock externo del CORE: {@code POST /core-mock/evento} con {@code x-api-key}.
 * Clave de prueba fijada aquí; en ejecución llega por CORE_MOCK_API_KEY.
 */
@WebMvcTest(controllers = CoreMockController.class, properties = "polizas.core-mock.api-key=123456")
@Import({CoreMockService.class, CoreMockControllerTest.Reloj.class})
@ExtendWith(OutputCaptureExtension.class)
class CoreMockControllerTest {

    private static final String URL = "/core-mock/evento";
    private static final String CUERPO = """
            {"evento": "ACTUALIZACION", "polizaId": 555}""";

    @TestConfiguration
    static class Reloj {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-16T20:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void conApiKeyValidaRegistraElIntentoEnLogsYResponde202(CapturedOutput salida) throws Exception {
        mvc.perform(post(URL).header("x-api-key", "123456")
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.registrado").value(true))
                .andExpect(jsonPath("$.evento").value("ACTUALIZACION"))
                .andExpect(jsonPath("$.polizaId").value(555))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());

        assertThat(salida.getOut())
                .contains("CORE-MOCK: se intentó enviar la operación al CORE (evento=ACTUALIZACION, polizaId=555)");
    }

    @Test
    void sinApiKeyResponde401YNoRegistraElIntento(CapturedOutput salida) throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("API_KEY_INVALIDA"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());

        assertThat(salida.getOut()).doesNotContain("se intentó enviar la operación al CORE");
    }

    @Test
    void conApiKeyIncorrectaResponde401SinRevelarLaClave(CapturedOutput salida) throws Exception {
        mvc.perform(post(URL).header("x-api-key", "clave-equivocada")
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("API_KEY_INVALIDA"));

        assertThat(salida.getOut()).doesNotContain("clave-equivocada").doesNotContain("123456");
    }

    @Test
    void cuerpoInvalidoResponde400() throws Exception {
        mvc.perform(post(URL).header("x-api-key", "123456").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"evento": "actualizacion\\nFALSO", "polizaId": -1}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    @Test
    void elFiltroNoAfectaOtrasRutas() throws Exception {
        // Sin x-api-key, una ruta fuera de /core-mock no recibe 401 (aquí no hay controlador: 404).
        mvc.perform(get("/api/v1/polizas/1"))
                .andExpect(status().isNotFound());
    }
}
