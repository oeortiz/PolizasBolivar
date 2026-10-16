package com.bolivar.polizas.dto.response;

import java.time.Instant;

/** Acuse del mock del CORE: el intento quedó registrado en el log; no se envió nada a un CORE real. */
public record CoreMockEventoResponse(
        boolean registrado,
        String evento,
        Long polizaId,
        Instant fechaRegistro,
        String correlationId,
        String mensaje
) {
}
