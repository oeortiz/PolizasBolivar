package com.bolivar.polizas.service;

import com.bolivar.polizas.entity.enums.OperacionNegocio;
import com.bolivar.polizas.exception.ValorInvalidoException;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Clave idempotente de una mutación. Si el cliente envía {@code Idempotency-Key}, la clave
 * almacenada es {@code OPERACION:entidad:clave}, de modo que la misma clave no colisiona entre
 * operaciones ni recursos distintos. Sin cabecera se genera una clave única (no hay reintento
 * que deduplicar y las reglas de estado siguen evitando duplicados).
 */
public record ClaveIdempotencia(String valor, boolean provistaPorCliente) {

    private static final Pattern FORMATO = Pattern.compile("^[A-Za-z0-9._:-]{1,60}$");

    public static ClaveIdempotencia de(OperacionNegocio operacion, Long entidadId, String claveCliente) {
        if (claveCliente == null || claveCliente.isBlank()) {
            return new ClaveIdempotencia(operacion + ":" + entidadId + ":" + UUID.randomUUID(), false);
        }
        String clave = claveCliente.trim();
        if (!FORMATO.matcher(clave).matches()) {
            throw new ValorInvalidoException(
                    "Idempotency-Key debe tener entre 1 y 60 caracteres: letras, dígitos, '.', '_', ':' o '-'");
        }
        return new ClaveIdempotencia(operacion + ":" + entidadId + ":" + clave, true);
    }
}
