package com.bolivar.polizas.integration.weblogic;

/** Fuente de tokens de acceso OAuth 2.0 para el servicio de mutaciones. */
public interface TokenProvider {

    /** Token vigente (desde caché o renovado). Lanza {@link TokenNoDisponibleException} si no se obtiene. */
    String token();

    /** Descarta el token en caché (p. ej. tras un 401). */
    void invalidar();
}
