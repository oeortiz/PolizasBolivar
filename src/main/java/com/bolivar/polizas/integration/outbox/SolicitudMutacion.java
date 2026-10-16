package com.bolivar.polizas.integration.outbox;

import com.bolivar.polizas.entity.enums.OperacionNegocio;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Mutación de negocio que debe sincronizarse con el CORE.
 *
 * @param entityId   id de la entidad remota (póliza o riesgo, según el mapeo)
 * @param payload    datos de negocio de la mutación ({@code mutation.payload} del contrato)
 */
public record SolicitudMutacion(
        OperacionNegocio operacion,
        Long entityId,
        String reasonCode,
        LocalDate effectiveDate,
        Map<String, Object> payload,
        String idempotencyKey,
        UUID correlationId
) {
}
