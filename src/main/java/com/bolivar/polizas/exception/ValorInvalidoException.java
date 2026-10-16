package com.bolivar.polizas.exception;

/** Dato de entrada inválido para una regla de cálculo (HTTP 400). */
public class ValorInvalidoException extends DominioException {

    private static final long serialVersionUID = 1L;

    public ValorInvalidoException(String mensaje) {
        super(CodigoError.VALOR_INVALIDO, mensaje);
    }
}
