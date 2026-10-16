package com.bolivar.polizas.dto.response;

import com.bolivar.polizas.service.renovacion.ResultadoRenovacionPoliza;

/**
 * Respuesta de POST /polizas/{id}/renovar. La póliza queda RENOVADA localmente; mientras
 * {@code operacion.estado} no sea CONFIRMADA la respuesta es 202 (no es un éxito definitivo).
 */
public record RenovacionResponse(
        PolizaResponse poliza,
        HistorialRenovacionResponse renovacion,
        OperacionResponse operacion,
        boolean repeticion
) {
    public static RenovacionResponse de(ResultadoRenovacionPoliza r) {
        return new RenovacionResponse(PolizaResponse.de(r.poliza()), HistorialRenovacionResponse.de(r.historial()),
                OperacionResponse.de(r.operacion()), r.repeticion());
    }
}
