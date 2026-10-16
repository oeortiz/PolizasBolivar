package com.bolivar.polizas.dto.request;

import com.bolivar.polizas.entity.Participante;
import com.bolivar.polizas.entity.enums.TipoDocumento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ParticipanteRequest(
        @NotNull TipoDocumento tipoDocumento,
        @NotBlank @Size(max = 20) @Pattern(regexp = "^[A-Za-z0-9-]+$",
                message = "solo admite letras, dígitos y guion") String numeroDocumento,
        @NotBlank @Size(max = 200) String nombre
) {
    public Participante aDominio() {
        return new Participante(tipoDocumento, numeroDocumento.trim(), nombre.trim());
    }
}
