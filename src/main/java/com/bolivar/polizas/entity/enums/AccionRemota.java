package com.bolivar.polizas.entity.enums;

/**
 * Acciones admitidas por el contrato remoto de WebLogic. El contrato no admite
 * RENOVAR ni CANCELAR: esas operaciones de negocio se traducen a estas acciones
 * en un componente de mapeo centralizado (Etapa 5).
 */
public enum AccionRemota {
    ANULAR,
    SUSPENDER,
    ACTIVAR,
    MODIFICAR;
}
