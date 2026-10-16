package com.bolivar.polizas.exception;

/**
 * Códigos de error de negocio estables, expuestos en las respuestas de la API.
 * La traducción a código HTTP se hace en el manejador centralizado (Etapa 3):
 * VALOR_INVALIDO y SOLICITUD_INVALIDA → 400; *_NO_ENCONTRADA/O → 404;
 * reglas de estado, concurrencia e idempotencia → 409; ERROR_INTERNO → 500.
 */
public enum CodigoError {
    VALOR_INVALIDO,
    SOLICITUD_INVALIDA,
    POLIZA_NO_ENCONTRADA,
    RIESGO_NO_ENCONTRADO,
    OPERACION_NO_ENCONTRADA,
    CONFLICTO_CONCURRENCIA,
    CONFLICTO_DATOS,
    IDEMPOTENCY_KEY_REUTILIZADA,
    ERROR_INTERNO,
    POLIZA_CANCELADA,
    RIESGO_ADICIONAL_EN_INDIVIDUAL,
    RIESGO_NO_PERTENECE_A_POLIZA,
    RIESGO_YA_CANCELADO,
    RIESGO_INDIVIDUAL_NO_CANCELABLE,
    ULTIMO_RIESGO_ACTIVO,
    /** Falta la constante de IPC del periodo: no se renueva ni se asume IPC = 0 (409). */
    IPC_NO_DISPONIBLE,
    /** La constante de IPC existe pero su valor no es un porcentaje válido (409). */
    IPC_INVALIDO,
    /** Hay una renovación anterior de la póliza aún no confirmada por el CORE (409). */
    RENOVACION_EN_CURSO,
    /** Solo una operación FALLIDA puede reabrirse manualmente (409). */
    OPERACION_NO_REINTENTABLE,
    /** Falta la cabecera x-api-key del mock del CORE o no coincide (401). */
    API_KEY_INVALIDA
}
