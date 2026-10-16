package com.bolivar.polizas.integration.simulated;

/** Comportamientos del adaptador simulado. */
public enum ModoSimulacion {
    /** Confirma todas las mutaciones (HTTP 200). */
    EXITO,
    /** Responde siempre el estado HTTP configurado (p. ej. 503 o 400). */
    ERROR_HTTP,
    /** Simula que el CORE no responde a tiempo: resultado incierto. */
    TIMEOUT,
    /** Falla {@code fallosAntesDeExito} veces por mutación con el estado configurado y luego confirma. */
    FALLA_Y_RECUPERA
}
