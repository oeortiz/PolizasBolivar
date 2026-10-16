package com.bolivar.polizas.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** Reloj controlable para probar reintentos sin esperar en tiempo real. */
public class RelojMutable extends Clock {

    private volatile Instant ahora;
    private final ZoneId zona;

    public RelojMutable(Instant inicio, ZoneId zona) {
        this.ahora = inicio;
        this.zona = zona;
    }

    public void avanzar(Duration duracion) {
        ahora = ahora.plus(duracion);
    }

    public void fijar(Instant instante) {
        ahora = instante;
    }

    @Override
    public ZoneId getZone() {
        return zona;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new RelojMutable(ahora, zone);
    }

    @Override
    public Instant instant() {
        return ahora;
    }
}
