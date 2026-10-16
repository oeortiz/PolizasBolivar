package com.bolivar.polizas.controller;

import com.bolivar.polizas.dto.response.MutacionResponse;
import com.bolivar.polizas.dto.response.OperacionResponse;
import com.bolivar.polizas.dto.response.RenovacionResponse;
import com.bolivar.polizas.entity.IntegrationOperation;
import com.bolivar.polizas.entity.enums.EstadoIntegracion;
import com.bolivar.polizas.service.ResultadoMutacion;
import com.bolivar.polizas.service.renovacion.ResultadoRenovacionPoliza;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;

/**
 * Código HTTP de una mutación según el estado REAL de su integración con el CORE:
 * <ul>
 *   <li>CONFIRMADA → 200 (solo posible al repetir una solicitud ya confirmada).</li>
 *   <li>Cualquier otro estado → 202 Accepted: el cambio local está persistido pero la
 *       sincronización no está confirmada. {@code Location} apunta al estado de la operación.</li>
 * </ul>
 */
final class RespuestasMutacion {

    private RespuestasMutacion() {
    }

    static ResponseEntity<MutacionResponse> aceptada(ResultadoMutacion resultado) {
        return respuesta(resultado.operacion(), MutacionResponse.de(resultado));
    }

    static ResponseEntity<RenovacionResponse> aceptada(ResultadoRenovacionPoliza resultado) {
        return respuesta(resultado.operacion(), RenovacionResponse.de(resultado));
    }

    private static <T> ResponseEntity<T> respuesta(IntegrationOperation operacion, T cuerpo) {
        HttpStatus status = operacion.getEstado() == EstadoIntegracion.CONFIRMADA
                ? HttpStatus.OK
                : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status)
                .location(URI.create(OperacionResponse.enlace(operacion)))
                .body(cuerpo);
    }
}
