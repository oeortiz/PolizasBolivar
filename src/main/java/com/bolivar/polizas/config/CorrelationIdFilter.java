package com.bolivar.polizas.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Propaga un correlationId por solicitud: lo toma de {@code X-Correlation-Id} si es un
 * UUID válido o genera uno nuevo. Queda en el MDC (logs), en la respuesta y en las
 * operaciones de integración que se registren durante la solicitud.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        UUID correlationId = parse(request.getHeader(HEADER));
        MDC.put(MDC_KEY, correlationId.toString());
        response.setHeader(HEADER, correlationId.toString());
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static UUID parse(String valor) {
        if (valor != null) {
            try {
                return UUID.fromString(valor.trim());
            } catch (IllegalArgumentException ignored) {
                // Valor no confiable: se reemplaza por uno nuevo.
            }
        }
        return UUID.randomUUID();
    }
}
