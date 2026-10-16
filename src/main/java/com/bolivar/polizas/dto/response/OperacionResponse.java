package com.bolivar.polizas.dto.response;

import com.bolivar.polizas.entity.IntegrationOperation;
import com.bolivar.polizas.entity.enums.AccionRemota;
import com.bolivar.polizas.entity.enums.AdaptadorIntegracion;
import com.bolivar.polizas.entity.enums.EstadoIntegracion;
import com.bolivar.polizas.entity.enums.OperacionNegocio;
import com.bolivar.polizas.entity.enums.TipoEntidad;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Estado de integración de una mutación con el CORE. {@code adaptador = SIMULADO}
 * significa que la confirmación no proviene del CORE real.
 */
public record OperacionResponse(
        UUID id,
        UUID correlationId,
        OperacionNegocio operacionNegocio,
        TipoEntidad entityType,
        Long entityId,
        AccionRemota accionRemota,
        LocalDate effectiveDate,
        EstadoIntegracion estado,
        int intentos,
        int maxIntentos,
        Instant proximoIntentoAt,
        Instant ultimoIntentoAt,
        Instant confirmadaAt,
        String ultimoError,
        AdaptadorIntegracion adaptador,
        Instant createdAt,
        String enlace
) {
    public static OperacionResponse de(IntegrationOperation op) {
        return new OperacionResponse(op.getId(), op.getCorrelationId(), op.getOperacionNegocio(), op.getEntityType(),
                op.getEntityId(), op.getAccionRemota(), op.getEffectiveDate(), op.getEstado(), op.getIntentos(),
                op.getMaxIntentos(), op.getProximoIntentoAt(), op.getUltimoIntentoAt(), op.getConfirmadaAt(),
                op.getUltimoError(), op.getAdaptador(), op.getCreatedAt(), enlace(op));
    }

    public static String enlace(IntegrationOperation op) {
        return "/api/v1/operaciones/" + op.getId();
    }
}
