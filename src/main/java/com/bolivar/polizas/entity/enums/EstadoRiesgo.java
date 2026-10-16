package com.bolivar.polizas.entity.enums;

/**
 * Estado contractual de un riesgo. Solo los riesgos ACTIVO suman a la prima consolidada.
 */
public enum EstadoRiesgo {
    ACTIVO,
    /** Estado final: no se puede volver a cancelar. */
    CANCELADO;
}
