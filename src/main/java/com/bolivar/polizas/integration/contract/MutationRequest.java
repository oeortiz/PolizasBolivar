package com.bolivar.polizas.integration.contract;

import com.bolivar.polizas.entity.enums.AccionRemota;
import com.bolivar.polizas.entity.enums.TipoEntidad;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;
import java.util.UUID;

/**
 * Cuerpo del endpoint unificado de mutaciones de WebLogic, según la estructura conceptual
 * del enunciado:
 *
 * <pre>
 * {
 *   "header":   {"correlationId", "timestamp", "channel", "user"},
 *   "mutation": {"entityType", "entityId", "action", "reasonCode", "effectiveDate", "payload"}
 * }
 * </pre>
 *
 * {@code entityType} y {@code action} son enumeraciones: el contrato no puede expresar valores
 * distintos de POLIZA/RIESGO ni de ANULAR/SUSPENDER/ACTIVAR/MODIFICAR. No se añaden campos que
 * el contrato no define; el identificador idempotente viaja como cabecera HTTP.
 */
public record MutationRequest(Header header, Mutation mutation) {

    public MutationRequest {
        Objects.requireNonNull(header, "header es obligatorio");
        Objects.requireNonNull(mutation, "mutation es obligatorio");
    }

    /**
     * @param timestamp ISO-8601 con zona, momento del intento de envío
     */
    public record Header(UUID correlationId, String timestamp, String channel, String user) {
        public Header {
            Objects.requireNonNull(correlationId, "correlationId es obligatorio");
            Objects.requireNonNull(timestamp, "timestamp es obligatorio");
            Objects.requireNonNull(channel, "channel es obligatorio");
            Objects.requireNonNull(user, "user es obligatorio");
        }
    }

    /**
     * @param effectiveDate ISO-8601 (fecha)
     * @param payload       datos de negocio de la mutación; se respeta tal cual se registró en el outbox
     */
    public record Mutation(TipoEntidad entityType, String entityId, AccionRemota action, String reasonCode,
                           String effectiveDate, JsonNode payload) {
        public Mutation {
            Objects.requireNonNull(entityType, "entityType es obligatorio");
            Objects.requireNonNull(entityId, "entityId es obligatorio");
            Objects.requireNonNull(action, "action es obligatorio");
            Objects.requireNonNull(effectiveDate, "effectiveDate es obligatorio");
            Objects.requireNonNull(payload, "payload es obligatorio");
        }
    }
}
