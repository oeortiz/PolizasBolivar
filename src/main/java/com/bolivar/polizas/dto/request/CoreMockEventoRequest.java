package com.bolivar.polizas.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Evento recibido por el mock externo del CORE, por ejemplo
 * {@code {"evento": "ACTUALIZACION", "polizaId": 555}}.
 *
 * <p>{@code evento} admite solo mayúsculas y guion bajo: además de validar la entrada, evita que
 * un valor con saltos de línea falsifique entradas en el log.</p>
 */
public record CoreMockEventoRequest(
        @NotBlank @Size(max = 50)
        @Pattern(regexp = "^[A-Z][A-Z_]*$", message = "solo admite mayúsculas y guion bajo (ej. ACTUALIZACION)")
        String evento,
        @NotNull @Positive Long polizaId
) {
}
