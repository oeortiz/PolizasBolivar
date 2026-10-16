package com.bolivar.polizas.integration.weblogic;

import com.bolivar.polizas.entity.enums.AccionRemota;
import com.bolivar.polizas.entity.enums.AdaptadorIntegracion;
import com.bolivar.polizas.entity.enums.TipoEntidad;
import com.bolivar.polizas.integration.contract.MutationRequest;
import com.bolivar.polizas.integration.port.ResultadoEnvio;
import com.bolivar.polizas.integration.port.SolicitudCore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adaptador real contra un servidor HTTP local que imita el endpoint de mutaciones.
 * TLS/mTLS no se ejercita aquí (lo configura {@code IntegracionConfig}); se prueba el
 * contrato, las cabeceras, la clasificación de respuestas y los timeouts reales.
 */
class WebLogicCoreAdapterTest {

    private final ObjectMapper json = new ObjectMapper();
    private HttpServer servidor;
    private final Map<String, String> cabeceras = new ConcurrentHashMap<>();
    private volatile String cuerpoRecibido;
    private volatile int statusRespuesta = 200;
    private volatile long demoraMs = 0;
    private final FakeTokens tokens = new FakeTokens();

    @BeforeEach
    void iniciar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/mutaciones", intercambio -> {
            intercambio.getRequestHeaders().forEach((k, v) -> cabeceras.put(k.toLowerCase(), v.getFirst()));
            cuerpoRecibido = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            try {
                Thread.sleep(demoraMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] respuesta = "{\"resultado\":\"OK\"}".getBytes(StandardCharsets.UTF_8);
            intercambio.sendResponseHeaders(statusRespuesta, respuesta.length);
            intercambio.getResponseBody().write(respuesta);
            intercambio.close();
        });
        servidor.start();
    }

    @AfterEach
    void detener() {
        servidor.stop(0);
    }

    @Test
    void enviaElContratoConOauthCorrelationIdEIdempotencyKey() throws Exception {
        SolicitudCore solicitud = solicitud();

        ResultadoEnvio r = adaptador(Duration.ofSeconds(2)).enviar(solicitud);

        assertThat(r).isInstanceOf(ResultadoEnvio.Confirmado.class);
        assertThat(r.adaptador()).isEqualTo(AdaptadorIntegracion.WEBLOGIC);
        assertThat(cabeceras.get("authorization")).isEqualTo("Bearer token-1");
        assertThat(cabeceras.get("idempotency-key")).isEqualTo(solicitud.idempotencyKey().toString());
        assertThat(cabeceras.get("x-correlation-id"))
                .isEqualTo(solicitud.request().header().correlationId().toString());
        assertThat(cabeceras.get("content-type")).isEqualTo("application/json");
        JsonNode cuerpo = json.readTree(cuerpoRecibido);
        assertThat(cuerpo.at("/mutation/action").asText()).isEqualTo("ANULAR");
        assertThat(cuerpo.at("/mutation/entityType").asText()).isEqualTo("POLIZA");
        assertThat(cuerpo.at("/header/channel").asText()).isEqualTo("PORTAL_CLIENTES");
    }

    @Test
    void clasificaErroresHttpDelCore() {
        statusRespuesta = 400;
        assertThat(adaptador(Duration.ofSeconds(2)).enviar(solicitud())).isInstanceOf(ResultadoEnvio.Rechazado.class);
        statusRespuesta = 503;
        assertThat(adaptador(Duration.ofSeconds(2)).enviar(solicitud())).isInstanceOf(ResultadoEnvio.FalloTransitorio.class);
        statusRespuesta = 504;
        assertThat(adaptador(Duration.ofSeconds(2)).enviar(solicitud())).isInstanceOf(ResultadoEnvio.Incierto.class);
    }

    @Test
    void unTimeoutDeLecturaEsResultadoIncierto() {
        demoraMs = 1_500;

        ResultadoEnvio r = adaptador(Duration.ofMillis(300)).enviar(solicitud());

        assertThat(r).isInstanceOf(ResultadoEnvio.Incierto.class);
        assertThat(r.detalle()).contains("Timeout");
        assertThat(r.httpStatus()).isNull();
    }

    @Test
    void sinConexionEsFalloTransitorioPorqueLaSolicitudNoLlego() throws IOException {
        int puertoCerrado;
        try (ServerSocket s = new ServerSocket(0)) {
            puertoCerrado = s.getLocalPort();
        }
        WebLogicCoreAdapter adaptador = new WebLogicCoreAdapter(HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:" + puertoCerrado + "/mutaciones"), Duration.ofSeconds(1), tokens, json);

        assertThat(adaptador.enviar(solicitud())).isInstanceOf(ResultadoEnvio.FalloTransitorio.class);
    }

    @Test
    void un401InvalidaElTokenParaElSiguienteIntento() {
        statusRespuesta = 401;

        ResultadoEnvio r = adaptador(Duration.ofSeconds(2)).enviar(solicitud());

        assertThat(r).isInstanceOf(ResultadoEnvio.FalloTransitorio.class);
        assertThat(tokens.invalidaciones.get()).isEqualTo(1);
    }

    @Test
    void sinTokenNoEnviaYEsFalloTransitorio() {
        tokens.fallar = true;

        ResultadoEnvio r = adaptador(Duration.ofSeconds(2)).enviar(solicitud());

        assertThat(r).isInstanceOf(ResultadoEnvio.FalloTransitorio.class);
        assertThat(cuerpoRecibido).isNull();
    }

    private WebLogicCoreAdapter adaptador(Duration readTimeout) {
        URI url = URI.create("http://127.0.0.1:" + servidor.getAddress().getPort() + "/mutaciones");
        return new WebLogicCoreAdapter(HttpClient.newHttpClient(), url, readTimeout, tokens, json);
    }

    private static SolicitudCore solicitud() {
        return new SolicitudCore(UUID.randomUUID(), new MutationRequest(
                new MutationRequest.Header(UUID.randomUUID(), "2026-12-20T10:30:00-05:00", "PORTAL_CLIENTES", "system_user"),
                new MutationRequest.Mutation(TipoEntidad.POLIZA, "1", AccionRemota.ANULAR, "CANCELAR_POLIZA",
                        "2026-12-20", new ObjectMapper().createObjectNode().put("polizaId", 1))));
    }

    private static final class FakeTokens implements TokenProvider {
        private final AtomicInteger invalidaciones = new AtomicInteger();
        private volatile boolean fallar;

        @Override
        public String token() {
            if (fallar) {
                throw new TokenNoDisponibleException("sin token");
            }
            return "token-1";
        }

        @Override
        public void invalidar() {
            invalidaciones.incrementAndGet();
        }
    }
}
