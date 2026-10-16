package com.bolivar.polizas.entity.enums;

/**
 * Estado técnico de la última mutación de una póliza o riesgo frente al CORE.
 */
public enum EstadoSincronizacion {
    /** No se ha emitido ninguna mutación hacia el CORE para este registro. */
    NO_REQUERIDA,
    /** Existe una operación de integración aún no confirmada. */
    PENDIENTE,
    /** El CORE confirmó la última mutación. */
    SINCRONIZADA,
    /** La última mutación fue rechazada o agotó sus reintentos; requiere revisión. */
    FALLIDA;
}
