package com.bolivar.polizas.integration.port;

import com.bolivar.polizas.integration.contract.MutationRequest;

import java.util.Objects;
import java.util.UUID;

/**
 * Mutación lista para enviarse al CORE.
 *
 * @param idempotencyKey identificador estable de la mutación (id de la operación del outbox).
 *                       Se envía en la cabecera {@code Idempotency-Key}; si el CORE no la
 *                       soporta, un reintento tras un resultado incierto puede duplicar la mutación.
 */
public record SolicitudCore(UUID idempotencyKey, MutationRequest request) {

    public SolicitudCore {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey es obligatorio");
        Objects.requireNonNull(request, "request es obligatorio");
    }
}
