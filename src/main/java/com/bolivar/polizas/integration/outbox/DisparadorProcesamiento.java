package com.bolivar.polizas.integration.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Event-Driven: envía la mutación en cuanto la transacción que la registró hace commit
 * ({@code AFTER_COMMIT}: si la transacción se revierte, el evento no se procesa). Se ejecuta
 * en un hilo aparte, por lo que la respuesta HTTP (202) no espera al CORE. Si el disparo se
 * pierde (cola llena, caída), el sondeo periódico la recoge igual: el outbox es la fuente de
 * verdad, el evento solo reduce la latencia.
 */
@Component
@ConditionalOnProperty(name = "polizas.integracion.procesador.habilitado", havingValue = "true", matchIfMissing = true)
public class DisparadorProcesamiento {

    private static final Logger log = LoggerFactory.getLogger(DisparadorProcesamiento.class);

    private final ProcesadorOperaciones procesador;

    public DisparadorProcesamiento(ProcesadorOperaciones procesador) {
        this.procesador = procesador;
    }

    @Async("procesadorExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alRegistrarOperacion(OperacionRegistradaEvent evento) {
        try {
            procesador.procesarOperacion(evento.operacionId());
        } catch (RuntimeException e) {
            log.warn("Disparo inmediato de la operación {} falló; la recogerá el sondeo", evento.operacionId(), e);
        }
    }
}
