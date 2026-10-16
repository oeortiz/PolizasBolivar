package com.bolivar.polizas.service;

import com.bolivar.polizas.config.CorrelationIdFilter;
import com.bolivar.polizas.config.PolizasProperties;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Quién y desde dónde se ejecuta la operación. Mientras no exista autenticación
 * (Spring Security se incorpora con la integración segura), el actor es el usuario
 * técnico configurado; el correlationId proviene del filtro HTTP.
 */
@Component
public class ContextoSolicitud {

    private final PolizasProperties properties;

    public ContextoSolicitud(PolizasProperties properties) {
        this.properties = properties;
    }

    public String actor() {
        return properties.usuarioSistema();
    }

    public String canal() {
        return properties.canal();
    }

    /** correlationId de la solicitud en curso, o uno nuevo fuera de un contexto HTTP. */
    public UUID correlationId() {
        String valor = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (valor != null) {
            try {
                return UUID.fromString(valor);
            } catch (IllegalArgumentException ignored) {
                // se genera uno nuevo
            }
        }
        return UUID.randomUUID();
    }
}
