package com.bolivar.polizas.service.renovacion;

import com.bolivar.polizas.entity.IntegrationOperation;
import com.bolivar.polizas.entity.Poliza;
import com.bolivar.polizas.entity.RenewalHistory;

/**
 * @param repeticion {@code true} si se repitió una {@code Idempotency-Key} ya procesada:
 *                   se devuelve el historial y la operación originales sin crear nada nuevo
 */
public record ResultadoRenovacionPoliza(Poliza poliza, RenewalHistory historial, IntegrationOperation operacion,
                                        boolean repeticion) {
}
