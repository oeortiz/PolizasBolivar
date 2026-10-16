package com.bolivar.polizas.dto.request;

import com.bolivar.polizas.entity.DatosRiesgo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Datos de un riesgo. La prima no se recibe: la calcula el servidor. */
public record RiesgoRequest(
        @NotBlank @Size(max = 250) String direccionInmueble,
        @NotNull @Valid ParticipanteRequest arrendatario,
        @NotNull @Valid ParticipanteRequest arrendador,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 2) BigDecimal canonMensual
) {
    public DatosRiesgo aDominio() {
        return new DatosRiesgo(direccionInmueble.trim(), arrendatario.aDominio(), arrendador.aDominio(), canonMensual);
    }
}
