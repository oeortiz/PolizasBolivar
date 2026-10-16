package com.bolivar.polizas.entity;

import com.bolivar.polizas.entity.enums.EstadoPoliza;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Valores de una póliza en un instante dado. {@link RenewalHistory} guarda dos
 * fotografías (anterior y nueva) para no depender de los valores actuales de la póliza.
 */
@Embeddable
public record FotografiaPoliza(
        @Column(name = "canon", precision = 15, scale = 2, nullable = false)
        BigDecimal canonMensual,

        @Column(name = "prima", precision = 17, scale = 2, nullable = false)
        BigDecimal prima,

        @Column(name = "fecha_inicio_vigencia", nullable = false)
        LocalDate fechaInicioVigencia,

        @Column(name = "fecha_fin_vigencia", nullable = false)
        LocalDate fechaFinVigencia,

        @Enumerated(EnumType.STRING)
        @Column(name = "estado", length = 20, nullable = false)
        EstadoPoliza estado
) {
    public FotografiaPoliza {
        Objects.requireNonNull(canonMensual, "canonMensual es obligatorio");
        Objects.requireNonNull(prima, "prima es obligatoria");
        Objects.requireNonNull(fechaInicioVigencia, "fechaInicioVigencia es obligatoria");
        Objects.requireNonNull(fechaFinVigencia, "fechaFinVigencia es obligatoria");
        Objects.requireNonNull(estado, "estado es obligatorio");
    }

    public static FotografiaPoliza de(Poliza poliza) {
        return new FotografiaPoliza(
                poliza.getCanonMensual(),
                poliza.getPrima(),
                poliza.getFechaInicioVigencia(),
                poliza.getFechaFinVigencia(),
                poliza.getEstado());
    }
}
