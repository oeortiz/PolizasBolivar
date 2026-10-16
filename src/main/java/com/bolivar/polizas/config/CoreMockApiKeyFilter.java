package com.bolivar.polizas.config;

import com.bolivar.polizas.exception.CodigoError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Seguridad mínima del mock del CORE: toda solicitud a {@code /core-mock/**} debe traer la
 * cabecera {@code x-api-key} con el valor de {@code polizas.core-mock.api-key}
 * (variable {@code CORE_MOCK_API_KEY}).
 *
 * <ul>
 *   <li>La comparación es de tiempo constante.</li>
 *   <li>Si la clave no está configurada, se rechaza todo (falla cerrada).</li>
 *   <li>La clave recibida nunca se escribe en el log ni en la respuesta.</li>
 *   <li>Solo aplica a {@code /core-mock/**}; el resto de la API no cambia.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10) // después de CorrelationIdFilter
public class CoreMockApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "x-api-key";
    public static final String RUTA = "/core-mock/";
    public static final String ESQUEMA_SEGURIDAD = "coreMockApiKey";

    private static final Logger log = LoggerFactory.getLogger(CoreMockApiKeyFilter.class);

    private final byte[] claveEsperada;
    private final ObjectMapper json;

    public CoreMockApiKeyFilter(@Value("${polizas.core-mock.api-key:}") String clave, ObjectMapper json) {
        this.claveEsperada = clave == null || clave.isBlank() ? null : clave.getBytes(StandardCharsets.UTF_8);
        this.json = json;
        if (claveEsperada == null) {
            log.warn("CORE_MOCK_API_KEY no está configurada: /core-mock/** rechazará todas las solicitudes (401)");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        return !ruta.startsWith(RUTA);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (claveValida(request.getHeader(HEADER))) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("CORE-MOCK: solicitud rechazada por x-api-key ausente o inválida ({} {})",
                request.getMethod(), request.getRequestURI());
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "La cabecera x-api-key es obligatoria y debe ser válida");
        problema.setType(URI.create("urn:polizas:error:" + CodigoError.API_KEY_INVALIDA.name().toLowerCase()));
        problema.setTitle(HttpStatus.UNAUTHORIZED.getReasonPhrase());
        problema.setInstance(URI.create(request.getRequestURI()));
        problema.setProperty("codigo", CodigoError.API_KEY_INVALIDA.name());
        problema.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        json.writeValue(response.getOutputStream(), problema);
    }

    private boolean claveValida(String recibida) {
        if (claveEsperada == null || recibida == null) {
            return false;
        }
        return MessageDigest.isEqual(claveEsperada, recibida.getBytes(StandardCharsets.UTF_8));
    }
}
