package com.bolivar.polizas.integration.weblogic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuth2ClientCredentialsTokenProviderTest {

    private HttpServer servidor;
    private final AtomicInteger solicitudes = new AtomicInteger();
    private volatile String ultimoFormulario;
    private volatile String ultimaAutorizacion;
    private volatile int status = 200;
    private final RelojMutable reloj = new RelojMutable(Instant.parse("2026-10-16T12:00:00Z"));

    @BeforeEach
    void iniciar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/token", intercambio -> {
            int n = solicitudes.incrementAndGet();
            ultimoFormulario = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            ultimaAutorizacion = intercambio.getRequestHeaders().getFirst("Authorization");
            byte[] cuerpo = ("{\"access_token\":\"tok-" + n + "\",\"token_type\":\"Bearer\",\"expires_in\":300}")
                    .getBytes(StandardCharsets.UTF_8);
            intercambio.sendResponseHeaders(status, cuerpo.length);
            intercambio.getResponseBody().write(cuerpo);
            intercambio.close();
        });
        servidor.start();
    }

    @AfterEach
    void detener() {
        servidor.stop(0);
    }

    @Test
    void solicitaElTokenConClientCredentialsYLoReutilizaMientrasEsteVigente() {
        OAuth2ClientCredentialsTokenProvider proveedor = proveedor();

        assertThat(proveedor.token()).isEqualTo("tok-1");
        assertThat(proveedor.token()).isEqualTo("tok-1");
        assertThat(solicitudes.get()).isEqualTo(1);
        assertThat(ultimoFormulario).isEqualTo("grant_type=client_credentials&scope=polizas.mutar");
        assertThat(ultimaAutorizacion).isEqualTo("Basic "
                + Base64.getEncoder().encodeToString("cliente:secreto".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void renuevaElTokenAntesDeQueExpire() {
        OAuth2ClientCredentialsTokenProvider proveedor = proveedor();
        proveedor.token();

        reloj.avanzar(Duration.ofSeconds(300).minus(OAuth2ClientCredentialsTokenProvider.MARGEN_RENOVACION));

        assertThat(proveedor.token()).isEqualTo("tok-2");
    }

    @Test
    void invalidarFuerzaUnTokenNuevo() {
        OAuth2ClientCredentialsTokenProvider proveedor = proveedor();
        proveedor.token();

        proveedor.invalidar();

        assertThat(proveedor.token()).isEqualTo("tok-2");
    }

    @Test
    void unErrorDelServidorDeTokensNoExponeCredenciales() {
        status = 401;

        assertThatThrownBy(() -> proveedor().token())
                .isInstanceOf(TokenNoDisponibleException.class)
                .hasMessageContaining("HTTP 401")
                .hasMessageNotContaining("secreto");
    }

    private OAuth2ClientCredentialsTokenProvider proveedor() {
        return new OAuth2ClientCredentialsTokenProvider(HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:" + servidor.getAddress().getPort() + "/token"),
                "cliente", "secreto", "polizas.mutar", Duration.ofSeconds(2), new ObjectMapper(), reloj);
    }

    private static final class RelojMutable extends Clock {
        private Instant ahora;

        RelojMutable(Instant inicio) {
            this.ahora = inicio;
        }

        void avanzar(Duration d) {
            ahora = ahora.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }
}
