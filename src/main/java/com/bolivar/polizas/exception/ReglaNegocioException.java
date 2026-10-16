package com.bolivar.polizas.exception;

/** Operación incompatible con el estado o el tipo de la póliza/riesgo (HTTP 409). */
public class ReglaNegocioException extends DominioException {

    private static final long serialVersionUID = 1L;

    public ReglaNegocioException(CodigoError codigo, String mensaje) {
        super(codigo, mensaje);
    }
}
