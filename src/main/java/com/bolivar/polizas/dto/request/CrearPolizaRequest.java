package com.bolivar.polizas.dto.request;

import com.bolivar.polizas.entity.enums.TipoPoliza;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;

/**
 * Alta de póliza (endpoint adicional, supuesto S-15).
 * INDIVIDUAL: exactamente un riesgo; el tomador es el arrendatario, por eso {@code tomador}
 * debe omitirse. COLECTIVA: {@code tomador} obligatorio y uno o varios riesgos.
 */
public record CrearPolizaRequest(
        @NotNull TipoPoliza tipo,
        @Valid ParticipanteRequest tomador,
        @NotNull LocalDate fechaInicioVigencia,
        @NotNull @Positive Integer duracionMeses,
        @NotEmpty List<@NotNull @Valid RiesgoRequest> riesgos
) {
}
