package com.bolivar.polizas.entity.enums;

/**
 * Ciclo de vida de una operación de integración (outbox).
 */
public enum EstadoIntegracion {
    /** Registrada de forma durable; pendiente de envío o de reintento. */
    PENDIENTE,
    /** Un procesador la tomó; si el proceso cae, la recuperación la reprograma. */
    EN_PROCESO,
    /** El CORE confirmó la mutación. Requiere resultado y fecha de confirmación. */
    CONFIRMADA,
    /** El CORE la rechazó por un error funcional; no se reintenta. */
    RECHAZADA,
    /** Reintentos agotados o resultado incierto; requiere revisión. */
    FALLIDA;
}
