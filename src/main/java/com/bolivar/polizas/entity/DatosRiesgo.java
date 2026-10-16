package com.bolivar.polizas.entity;

import java.math.BigDecimal;

/**
 * Datos de entrada para crear un riesgo. La prima no forma parte de la entrada:
 * siempre la calcula el agregado {@code Poliza}.
 */
public record DatosRiesgo(
        String direccionInmueble,
        Participante arrendatario,
        Participante arrendador,
        BigDecimal canonMensual
) {
}
