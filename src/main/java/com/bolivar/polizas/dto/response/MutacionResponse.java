package com.bolivar.polizas.dto.response;

import com.bolivar.polizas.service.ResultadoMutacion;

/**
 * Respuesta de una mutación aceptada: el cambio local ya está persistido y la
 * sincronización con el CORE se informa en {@code operacion}. Mientras la operación no
 * esté CONFIRMADA la respuesta es 202; no es un éxito definitivo.
 *
 * @param repeticion {@code true} si se reconoció una {@code Idempotency-Key} ya usada y se
 *                   devolvió el resultado original sin volver a ejecutar la operación
 */
public record MutacionResponse(
        PolizaResponse poliza,
        RiesgoResponse riesgo,
        OperacionResponse operacion,
        boolean repeticion
) {
    public static MutacionResponse de(ResultadoMutacion r) {
        return new MutacionResponse(
                PolizaResponse.de(r.poliza()),
                r.riesgo() == null ? null : RiesgoResponse.de(r.riesgo()),
                OperacionResponse.de(r.operacion()),
                r.repeticion());
    }
}
