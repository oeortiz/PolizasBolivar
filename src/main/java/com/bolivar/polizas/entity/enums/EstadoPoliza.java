package com.bolivar.polizas.entity.enums;

/**
 * Estado contractual de la póliza. No incluye el estado técnico de integración
 * con el CORE, que se modela por separado en {@link EstadoSincronizacion}.
 */
public enum EstadoPoliza {
    ACTIVA,
    RENOVADA,
    /** Estado final: no admite renovación ni nuevos riesgos. */
    CANCELADA;
}
