package com.bolivar.polizas.service;

import com.bolivar.polizas.entity.IntegrationOperation;
import com.bolivar.polizas.entity.Poliza;
import com.bolivar.polizas.entity.Riesgo;

/**
 * Resultado de una mutación local registrada para el CORE.
 *
 * @param riesgo     riesgo afectado, o {@code null} si la mutación es solo de póliza
 * @param repeticion {@code true} si la solicitud repitió una {@code Idempotency-Key} ya procesada
 */
public record ResultadoMutacion(IntegrationOperation operacion, Poliza poliza, Riesgo riesgo, boolean repeticion) {
}
