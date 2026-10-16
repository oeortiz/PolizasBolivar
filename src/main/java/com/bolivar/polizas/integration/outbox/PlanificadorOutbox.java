package com.bolivar.polizas.integration.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sondeo periódico del outbox y recuperación al arrancar. Garantiza que toda operación
 * persistida se procese aunque el disparo inmediato se haya perdido o la aplicación se
 * haya reiniciado.
 */
@Component
@ConditionalOnProperty(name = "polizas.integracion.procesador.habilitado", havingValue = "true", matchIfMissing = true)
public class PlanificadorOutbox {

    private static final Logger log = LoggerFactory.getLogger(PlanificadorOutbox.class);
    private static final int MAX_LOTES_POR_CICLO = 10;

    private final ProcesadorOperaciones procesador;

    public PlanificadorOutbox(ProcesadorOperaciones procesador) {
        this.procesador = procesador;
    }

    /** Tras un reinicio: retoma las operaciones que quedaron EN_PROCESO. */
    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        int recuperadas = procesador.recuperarInterrumpidas();
        if (recuperadas > 0) {
            log.warn("Recuperadas {} operaciones interrumpidas por un reinicio", recuperadas);
        }
    }

    @Scheduled(fixedDelayString = "${polizas.integracion.procesador.intervalo-sondeo:5s}",
            initialDelayString = "${polizas.integracion.procesador.intervalo-sondeo:5s}")
    public void ciclo() {
        try {
            procesador.recuperarInterrumpidas();
            for (int lote = 0; lote < MAX_LOTES_POR_CICLO && procesador.procesarPendientes() > 0; lote++) {
                // Se sigue mientras haya operaciones vencidas, con un tope por ciclo.
            }
        } catch (RuntimeException e) {
            log.error("Error en el ciclo del procesador del outbox", e);
        }
    }
}
