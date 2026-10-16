package com.bolivar.polizas.exception;

import java.util.Objects;

/** Base de los errores de negocio. Nunca debe transportar datos sensibles en el mensaje. */
public abstract class DominioException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final CodigoError codigo;

    protected DominioException(CodigoError codigo, String mensaje) {
        super(mensaje);
        this.codigo = Objects.requireNonNull(codigo);
    }

    public CodigoError getCodigo() {
        return codigo;
    }
}
