package com.bolivar.polizas.domain;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Regla configurable para elegir el mes de IPC aplicable en una renovación (supuesto S-10).
 * El enunciado no la define; la propuesta por defecto es {@link #MES_INICIO_NUEVA_VIGENCIA}.
 */
public enum ReglaMesIpc {

    /** Mes en que inicia la nueva vigencia (p. ej. renovación desde 2027-01-01 → IPC 2027-01). */
    MES_INICIO_NUEVA_VIGENCIA,

    /** Mes anterior al inicio de la nueva vigencia (útil si el IPC se publica con un mes de rezago). */
    MES_ANTERIOR_INICIO_NUEVA_VIGENCIA,

    /** Mes en que se ejecuta la renovación. */
    MES_FECHA_RENOVACION;

    public PeriodoIpc periodo(LocalDate inicioNuevaVigencia, LocalDate fechaRenovacion) {
        YearMonth periodo = switch (this) {
            case MES_INICIO_NUEVA_VIGENCIA -> YearMonth.from(inicioNuevaVigencia);
            case MES_ANTERIOR_INICIO_NUEVA_VIGENCIA -> YearMonth.from(inicioNuevaVigencia).minusMonths(1);
            case MES_FECHA_RENOVACION -> YearMonth.from(fechaRenovacion);
        };
        return new PeriodoIpc(periodo);
    }
}
