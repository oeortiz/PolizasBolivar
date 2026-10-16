package com.bolivar.polizas.integration.weblogic;

import com.bolivar.polizas.entity.enums.AdaptadorIntegracion;
import com.bolivar.polizas.integration.port.CoreMutationPort;
import com.bolivar.polizas.integration.port.ResultadoEnvio;
import com.bolivar.polizas.integration.port.SolicitudCore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Objects;

/**
 * Adaptador real hacia el servicio de mutaciones publicado en WebLogic.
 *
 * <ul>
 *   <li>HTTPS con mTLS y TLS 1.2+ (configurado en el {@link HttpClient}, ver {@code IntegracionConfig}).</li>
 *   <li>OAuth 2.0: {@code Authorization: Bearer} obtenido de {@link TokenProvider}.</li>
 *   <li>Cabeceras de trazabilidad: {@code X-Correlation-Id} y {@code Idempotency-Key} (id estable de la
 *       mutación). Que el CORE respete esta cabecera debe confirmarse (supuesto S-25).</li>
 *   <li>Timeout de lectura por solicitud; un intento por invocación.</li>
 *   <li>El payload no se escribe en logs (contiene datos personales).</li>
 * </ul>
 * No hay URL, credenciales ni certificados por defecto: todo llega por configuración externa.
 */
public class WebLogicCoreAdapter implements CoreMutationPort {

    private static final Logger log = LoggerFactory.getLogger(WebLogicCoreAdapter.class);
    private static final AdaptadorIntegracion ADAPTADOR = AdaptadorIntegracion.WEBLOGIC;

    private final HttpClient httpClient;
    private final URI mutacionesUrl;
    private final Duration readTimeout;
    private final TokenProvider tokenProvider;
    private final ObjectMapper objectMapper;

    public WebLogicCoreAdapter(HttpClient httpClient, URI mutacionesUrl, Duration readTimeout,
                               TokenProvider tokenProvider, ObjectMapper objectMapper) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.mutacionesUrl = Objects.requireNonNull(mutacionesUrl);
        this.readTimeout = Objects.requireNonNull(readTimeout);
        this.tokenProvider = Objects.requireNonNull(tokenProvider);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public AdaptadorIntegracion tipo() {
        return ADAPTADOR;
    }

    @Override
    public ResultadoEnvio enviar(SolicitudCore solicitud) {
        String cuerpo;
        try {
            cuerpo = objectMapper.writeValueAsString(solicitud.request());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar la mutación " + solicitud.idempotencyKey(), e);
        }

        String token;
        try {
            token = tokenProvider.token();
        } catch (TokenNoDisponibleException e) {
            log.warn("Mutación {} no enviada: {}", solicitud.idempotencyKey(), e.getMessage());
            return new ResultadoEnvio.FalloTransitorio(null, "Token OAuth no disponible: " + e.getMessage(), ADAPTADOR);
        }

        HttpRequest request = HttpRequest.newBuilder(mutacionesUrl)
                .timeout(readTimeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token)
                .header("X-Correlation-Id", solicitud.request().header().correlationId().toString())
                .header("Idempotency-Key", solicitud.idempotencyKey().toString())
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) {
                tokenProvider.invalidar();
            }
            ResultadoEnvio resultado = ClasificadorRespuestaCore.clasificar(response.statusCode(), response.body());
            log.info("Mutación {} ({} {}) → HTTP {} → {}", solicitud.idempotencyKey(),
                    solicitud.request().mutation().action(), solicitud.request().mutation().entityType(),
                    response.statusCode(), resultado.getClass().getSimpleName());
            return resultado;
        } catch (HttpConnectTimeoutException | ConnectException e) {
            // No se estableció conexión: la solicitud no llegó al CORE.
            return registrar(solicitud, new ResultadoEnvio.FalloTransitorio(null,
                    "Sin conexión con el CORE: " + e.getClass().getSimpleName(), ADAPTADOR));
        } catch (SSLHandshakeException e) {
            // Falló el handshake (certificado de cliente rechazado, CA desconocida, protocolo):
            // la mutación no llegó al CORE. Suele ser un error de configuración que requiere revisión.
            return registrar(solicitud, new ResultadoEnvio.FalloTransitorio(null,
                    "Fallo del handshake TLS/mTLS con el CORE: " + e.getMessage(), ADAPTADOR));
        } catch (HttpTimeoutException e) {
            // La solicitud se envió y no hubo respuesta a tiempo: el CORE pudo haberla aplicado.
            return registrar(solicitud, new ResultadoEnvio.Incierto(null,
                    "Timeout esperando respuesta del CORE (" + readTimeout.toMillis() + " ms)", ADAPTADOR));
        } catch (IOException e) {
            return registrar(solicitud, new ResultadoEnvio.Incierto(null,
                    "Error de E/S con resultado desconocido: " + e.getClass().getSimpleName(), ADAPTADOR));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return registrar(solicitud, new ResultadoEnvio.Incierto(null, "Envío interrumpido", ADAPTADOR));
        }
    }

    private static ResultadoEnvio registrar(SolicitudCore solicitud, ResultadoEnvio resultado) {
        log.warn("Mutación {} → {}: {}", solicitud.idempotencyKey(), resultado.getClass().getSimpleName(),
                resultado.detalle());
        return resultado;
    }
}
