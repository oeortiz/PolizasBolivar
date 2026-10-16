package com.bolivar.polizas.dto.response;

import com.bolivar.polizas.entity.RenewalHistory;
import com.fasterxml.jackson.annotation.JsonRawValue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Registro inmutable de una renovación: valores anteriores y nuevos, IPC aplicado y la
 * constante de la que se obtuvo. No depende de los valores actuales de la póliza.
 *
 * @param riesgos detalle por riesgo (JSON tal como se guardó en el historial)
 */
public record HistorialRenovacionResponse(
        Long id,
        Long polizaId,
        Instant fechaRenovacion,
        BigDecimal ipcAplicado,
        String ipcConstante,
        int duracionMeses,
        FotografiaResponse anterior,
        FotografiaResponse nueva,
        @JsonRawValue String riesgos,
        UUID integrationOperationId,
        String usuario
) {
    public static HistorialRenovacionResponse de(RenewalHistory h) {
        return new HistorialRenovacionResponse(h.getId(), h.getPoliza().getId(), h.getFechaRenovacion(),
                h.getIpcAplicado(), h.getIpcConstanteNombre(), h.getDuracionMeses(),
                FotografiaResponse.de(h.getAnterior()), FotografiaResponse.de(h.getNueva()),
                h.getDetalleRiesgos(), h.getIntegrationOperationId(), h.getUsuario());
    }
}
