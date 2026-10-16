package com.bolivar.polizas.entity.enums;

/**
 * Resultado registrado en la bitácora de auditoría.
 */
public enum ResultadoAuditoria {
    /** Operación completada de forma definitiva. */
    EXITO,
    /** Cambio local persistido; integración con el CORE aún pendiente (HTTP 202). */
    ACEPTADO_PENDIENTE,
    /** Rechazada por validación o regla de negocio. */
    RECHAZADO,
    /** Error técnico. */
    ERROR;
}
