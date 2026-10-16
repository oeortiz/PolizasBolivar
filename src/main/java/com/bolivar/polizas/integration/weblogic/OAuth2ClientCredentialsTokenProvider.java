package com.bolivar.polizas.integration.weblogic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/**
 * OAuth 2.0 Client Credentials (RFC 6749 §4.4) con caché del token.
 *
 * <ul>
 *   <li>Autenticación del cliente con HTTP Basic ({@code client_secret_basic}).</li>
 *   <li>Se usa el mismo {@link HttpClient} que las mutaciones, por lo que también viaja por mTLS.</li>
 *   <li>El token se renueva cuando faltan menos de {@link #MARGEN_RENOVACION} para su expiración.</li>
 *   <li>Credenciales y tokens nunca se escriben en logs ni en mensajes de error.</li>
 * </ul>
 * El método exacto de autenticación del cliente y el scope deben confirmarse con el CORE.
 */
public class OAuth2ClientCredentialsTokenProvider implements TokenProvider {

    static final Duration MARGEN_RENOVACION = Duration.ofSeconds(30);
    private static final Duration EXPIRACION_POR_DEFECTO = Duration.ofMinutes(5);

    private final HttpClient httpClient;
    private final URI tokenUrl;
    private final String clientId;
    private final String clientSecret;
    private final String scope;
    private final Duration timeout;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    private String tokenActual;
    private Instant expiraEn = Instant.MIN;

    public OAuth2ClientCredentialsTokenProvider(HttpClient httpClient, URI tokenUrl, String clientId,
                                                String clientSecret, String scope, Duration timeout,
                                                ObjectMapper objectMapper, Clock clock) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.tokenUrl = Objects.requireNonNull(tokenUrl);
        this.clientId = Objects.requireNonNull(clientId);
        this.clientSecret = Objects.requireNonNull(clientSecret);
        this.scope = scope;
        this.timeout = Objects.requireNonNull(timeout);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public synchronized String token() {
        if (tokenActual != null && clock.instant().isBefore(expiraEn.minus(MARGEN_RENOVACION))) {
            return tokenActual;
        }
        solicitarToken();
        return tokenActual;
    }

    @Override
    public synchronized void invalidar() {
        tokenActual = null;
        expiraEn = Instant.MIN;
    }

    private void solicitarToken() {
        String formulario = "grant_type=client_credentials"
                + (scope == null || scope.isBlank() ? "" : "&scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8));
        String credenciales = Base64.getEncoder().encodeToString(
                (URLEncoder.encode(clientId, StandardCharsets.UTF_8) + ":"
                        + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(tokenUrl)
                .timeout(timeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .header("Authorization", "Basic " + credenciales)
                .POST(HttpRequest.BodyPublishers.ofString(formulario))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new TokenNoDisponibleException("No se pudo contactar el servidor de tokens: " + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TokenNoDisponibleException("Solicitud de token interrumpida", e);
        }
        if (response.statusCode() != 200) {
            throw new TokenNoDisponibleException("El servidor de tokens respondió HTTP " + response.statusCode());
        }
        try {
            JsonNode json = objectMapper.readTree(response.body());
            JsonNode accessToken = json.get("access_token");
            if (accessToken == null || accessToken.asText().isBlank()) {
                throw new TokenNoDisponibleException("Respuesta de token sin access_token");
            }
            JsonNode tipo = json.get("token_type");
            if (tipo != null && !"bearer".equalsIgnoreCase(tipo.asText())) {
                throw new TokenNoDisponibleException("Tipo de token no soportado: " + tipo.asText());
            }
            JsonNode expiresIn = json.get("expires_in");
            Duration vigencia = expiresIn != null && expiresIn.canConvertToLong() && expiresIn.asLong() > 0
                    ? Duration.ofSeconds(expiresIn.asLong())
                    : EXPIRACION_POR_DEFECTO;
            this.tokenActual = accessToken.asText();
            this.expiraEn = clock.instant().plus(vigencia);
        } catch (IOException e) {
            throw new TokenNoDisponibleException("Respuesta de token ilegible", e);
        }
    }
}
