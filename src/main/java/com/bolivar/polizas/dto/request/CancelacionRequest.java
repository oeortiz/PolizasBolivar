package com.bolivar.polizas.dto.request;

import jakarta.validation.constraints.Size;

/** Cuerpo opcional de las cancelaciones. */
public record CancelacionRequest(@Size(max = 250) String motivo) {
}
