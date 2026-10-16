package com.bolivar.polizas.dto.response;

import com.bolivar.polizas.entity.Poliza;
import com.bolivar.polizas.entity.enums.EstadoPoliza;
import com.bolivar.polizas.entity.enums.EstadoSincronizacion;
import com.bolivar.polizas.entity.enums.TipoPoliza;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Representación de una póliza. {@code prima} es la de su único riesgo (INDIVIDUAL) o la
 * consolidada de los riesgos activos (COLECTIVA). {@code estadoSincronizacion} indica si
 * la última mutación ya fue confirmada por el CORE.
 */
public record PolizaResponse(
        Long id,
        TipoPoliza tipo,
        EstadoPoliza estado,
        EstadoSincronizacion estadoSincronizacion,
        BigDecimal canonMensual,
        BigDecimal prima,
        LocalDate fechaInicioVigencia,
        LocalDate fechaFinVigencia,
        int duracionMeses,
        ParticipanteResponse tomador,
        Instant fechaCancelacion,
        String motivoCancelacion,
        Instant createdAt,
        Instant updatedAt
) {
    public static PolizaResponse de(Poliza p) {
        return new PolizaResponse(p.getId(), p.getTipo(), p.getEstado(), p.getEstadoSincronizacion(),
                p.getCanonMensual(), p.getPrima(), p.getFechaInicioVigencia(), p.getFechaFinVigencia(),
                p.getDuracionMeses(), ParticipanteResponse.de(p.getTomador()), p.getFechaCancelacion(),
                p.getMotivoCancelacion(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
