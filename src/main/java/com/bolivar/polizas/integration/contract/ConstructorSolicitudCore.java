package com.bolivar.polizas.integration.contract;

import com.bolivar.polizas.config.PolizasProperties;
import com.bolivar.polizas.entity.IntegrationOperation;
import com.bolivar.polizas.integration.port.SolicitudCore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Traduce una {@link IntegrationOperation} del outbox a la solicitud del contrato remoto.
 *
 * <ul>
 *   <li>{@code correlationId} y el identificador idempotente (id de la operación) son los
 *       registrados en el outbox: no cambian entre reintentos.</li>
 *   <li>{@code timestamp} es el momento de cada intento.</li>
 *   <li>{@code channel} y {@code user} provienen de la configuración (supuesto S-19).</li>
 *   <li>El payload se envía exactamente como se persistió.</li>
 * </ul>
 */
@Component
public class ConstructorSolicitudCore {

    private final ObjectMapper objectMapper;
    private final PolizasProperties properties;
    private final Clock clock;

    public ConstructorSolicitudCore(ObjectMapper objectMapper, PolizasProperties properties, Clock clock) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    public SolicitudCore construir(IntegrationOperation operacion) {
        MutationRequest.Header header = new MutationRequest.Header(
                operacion.getCorrelationId(),
                OffsetDateTime.now(clock).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                properties.canal(),
                properties.usuarioSistema());
        MutationRequest.Mutation mutation = new MutationRequest.Mutation(
                operacion.getEntityType(),
                String.valueOf(operacion.getEntityId()),
                operacion.getAccionRemota(),
                operacion.getReasonCode(),
                operacion.getEffectiveDate().format(DateTimeFormatter.ISO_LOCAL_DATE),
                leerPayload(operacion));
        return new SolicitudCore(operacion.getId(), new MutationRequest(header, mutation));
    }

    private JsonNode leerPayload(IntegrationOperation operacion) {
        try {
            return objectMapper.readTree(operacion.getPayload());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Payload ilegible en la operación " + operacion.getId(), e);
        }
    }
}
