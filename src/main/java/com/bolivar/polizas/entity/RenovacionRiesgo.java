package com.bolivar.polizas.entity;

import java.math.BigDecimal;

/** Valores de un riesgo antes y después de una renovación (se guarda en el historial). */
public record RenovacionRiesgo(
        Long riesgoId,
        BigDecimal canonAnterior,
        BigDecimal canonNuevo,
        BigDecimal primaAnterior,
        BigDecimal primaNueva
) {
}
