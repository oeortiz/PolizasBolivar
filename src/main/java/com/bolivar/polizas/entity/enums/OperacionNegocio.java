package com.bolivar.polizas.entity.enums;

/**
 * Operaciones de negocio que generan una mutación a sincronizar con el CORE.
 * Su traducción a {@link AccionRemota} no vive aquí, sino en el mapeador de integración.
 */
public enum OperacionNegocio {
    CANCELAR_POLIZA,
    RENOVAR_POLIZA,
    MODIFICAR_POLIZA,
    AGREGAR_RIESGO,
    CANCELAR_RIESGO,
    MODIFICAR_RIESGO;
}
