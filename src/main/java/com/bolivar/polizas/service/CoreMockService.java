package com.bolivar.polizas.service;

import com.bolivar.polizas.config.CorrelationIdFilter;
import com.bolivar.polizas.dto.request.CoreMockEventoRequest;
import com.bolivar.polizas.dto.response.CoreMockEventoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Mock externo del CORE. Su único propósito es dejar en el log que la operación se intentó
 * enviar al CORE. No consulta ni modifica pólizas, no escribe en la base de datos y no llama
 * a ningún sistema externo.
 */
@Service
public class CoreMockService {

    /** Logger con nombre propio para poder filtrar estos eventos en el agregador de logs. */
    static final Logger LOG = LoggerFactory.getLogger("core-mock");

    private final Clock clock;

    public CoreMockService(Clock clock) {
        this.clock = clock;
    }

    public CoreMockEventoResponse registrarIntento(CoreMockEventoRequest evento) {
        Instant ahora = clock.instant();
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        // Con el perfil json los pares clave-valor salen como campos del log ECS.
        LOG.atInfo()
                .addKeyValue("evento", evento.evento())
                .addKeyValue("polizaId", evento.polizaId())
                .log("CORE-MOCK: se intentó enviar la operación al CORE (evento={}, polizaId={})",
                        evento.evento(), evento.polizaId());
        return new CoreMockEventoResponse(true, evento.evento(), evento.polizaId(), ahora, correlationId,
                "Intento de envío al CORE registrado en logs (mock: no se envió nada a un CORE real)");
    }
}
