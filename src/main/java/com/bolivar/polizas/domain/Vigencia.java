package com.bolivar.polizas.domain;

import com.bolivar.polizas.exception.ValorInvalidoException;

import java.time.LocalDate;

/**
 * Periodo de vigencia de una póliza (supuesto S-02).
 *
 * <p>{@code fechaFin = fechaInicio + duracionMeses} con {@link LocalDate#plusMonths(long)}.
 * Si el día no existe en el mes destino se ajusta al último día del mes
 * (31-ene + 1 mes = 28/29-feb). La renovación inicia en la fecha fin anterior y
 * conserva la duración inicial.</p>
 */
public record Vigencia(LocalDate fechaInicio, LocalDate fechaFin, int duracionMeses) {

    public Vigencia {
        if (fechaInicio == null || fechaFin == null) {
            throw new ValorInvalidoException("Las fechas de vigencia son obligatorias");
        }
        if (duracionMeses <= 0) {
            throw new ValorInvalidoException("La duración de la vigencia debe ser mayor que cero meses");
        }
        if (!fechaFin.isAfter(fechaInicio)) {
            throw new ValorInvalidoException("La fecha fin de vigencia debe ser posterior a la fecha de inicio");
        }
    }

    public static Vigencia desde(LocalDate fechaInicio, int duracionMeses) {
        if (fechaInicio == null) {
            throw new ValorInvalidoException("La fecha de inicio de vigencia es obligatoria");
        }
        if (duracionMeses <= 0) {
            throw new ValorInvalidoException("La duración de la vigencia debe ser mayor que cero meses");
        }
        return new Vigencia(fechaInicio, fechaInicio.plusMonths(duracionMeses), duracionMeses);
    }

    /** Vigencia de la renovación: empieza donde termina la actual, con la misma duración. */
    public Vigencia siguiente() {
        return desde(fechaFin, duracionMeses);
    }
}
