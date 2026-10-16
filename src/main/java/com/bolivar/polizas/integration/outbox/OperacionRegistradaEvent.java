package com.bolivar.polizas.integration.outbox;

import java.util.UUID;

/**
 * Evento de dominio: se registró una mutación en el outbox. Se consume solo después del
 * commit de la transacción que la creó (nunca antes), para disparar su envío sin esperar al
 * siguiente ciclo de sondeo.
 */
public record OperacionRegistradaEvent(UUID operacionId) {
}
