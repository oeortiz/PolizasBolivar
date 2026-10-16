package com.bolivar.polizas.entity;

import java.util.List;

/**
 * Resultado de {@link Poliza#renovar}: fotografías anterior y nueva de la póliza y
 * detalle por riesgo activo renovado.
 */
public record ResultadoRenovacion(FotografiaPoliza anterior, FotografiaPoliza nueva, List<RenovacionRiesgo> riesgos) {

    public ResultadoRenovacion {
        riesgos = List.copyOf(riesgos);
    }
}
