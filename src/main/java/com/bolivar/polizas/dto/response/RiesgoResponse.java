package com.bolivar.polizas.dto.response;

import com.bolivar.polizas.entity.Riesgo;
import com.bolivar.polizas.entity.enums.EstadoRiesgo;
import com.bolivar.polizas.entity.enums.EstadoSincronizacion;

import java.math.BigDecimal;
import java.time.Instant;

public record RiesgoResponse(
        Long id,
        Long polizaId,
        EstadoRiesgo estado,
        EstadoSincronizacion estadoSincronizacion,
        String direccionInmueble,
        BigDecimal canonMensual,
        BigDecimal prima,
        ParticipanteResponse arrendatario,
        ParticipanteResponse arrendador,
        Instant fechaCancelacion,
        String motivoCancelacion,
        Instant createdAt,
        Instant updatedAt
) {
    public static RiesgoResponse de(Riesgo r) {
        return new RiesgoResponse(r.getId(), r.getPoliza().getId(), r.getEstado(), r.getEstadoSincronizacion(),
                r.getDireccionInmueble(), r.getCanonMensual(), r.getPrima(),
                ParticipanteResponse.de(r.getArrendatario()), ParticipanteResponse.de(r.getArrendador()),
                r.getFechaCancelacion(), r.getMotivoCancelacion(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
