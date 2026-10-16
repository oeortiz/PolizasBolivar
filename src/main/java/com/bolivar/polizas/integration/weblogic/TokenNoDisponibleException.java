package com.bolivar.polizas.integration.weblogic;

/** No fue posible obtener un token de acceso. El mensaje nunca incluye credenciales. */
public class TokenNoDisponibleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TokenNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public TokenNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
