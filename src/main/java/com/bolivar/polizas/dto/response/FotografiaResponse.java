package com.bolivar.polizas.dto.response;

import com.bolivar.polizas.entity.FotografiaPoliza;
import com.bolivar.polizas.entity.enums.EstadoPoliza;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FotografiaResponse(BigDecimal canonMensual, BigDecimal prima, LocalDate fechaInicioVigencia,
                                 LocalDate fechaFinVigencia, EstadoPoliza estado) {

    public static FotografiaResponse de(FotografiaPoliza f) {
        return new FotografiaResponse(f.canonMensual(), f.prima(), f.fechaInicioVigencia(),
                f.fechaFinVigencia(), f.estado());
    }
}
