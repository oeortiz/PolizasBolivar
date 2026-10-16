package com.bolivar.polizas.integration.outbox;

import com.bolivar.polizas.config.PolizasProperties;
import com.bolivar.polizas.entity.IntegrationOperation;
import com.bolivar.polizas.integration.mapping.DestinoRemoto;
import com.bolivar.polizas.integration.mapping.MapeadorOperacionCore;
import com.bolivar.polizas.repository.IntegrationOperationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Registra de forma durable la intención de sincronizar una mutación con el CORE
 * (Transactional Outbox). {@code Propagation.MANDATORY} obliga a que se ejecute dentro
 * de la misma transacción local que el cambio de dominio: o se guardan ambos o ninguno.
 * El envío real lo hace {@link ProcesadorOperaciones}, nunca este método.
 */
@Service
public class RegistroOperacionIntegracion {

    private final IntegrationOperationRepository repository;
    private final MapeadorOperacionCore mapeador;
    private final ObjectMapper objectMapper;
    private final PolizasProperties properties;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    public RegistroOperacionIntegracion(IntegrationOperationRepository repository,
                                        MapeadorOperacionCore mapeador,
                                        ObjectMapper objectMapper,
                                        PolizasProperties properties,
                                        ApplicationEventPublisher eventos,
                                        Clock clock) {
        this.repository = repository;
        this.mapeador = mapeador;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.eventos = eventos;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public IntegrationOperation registrar(SolicitudMutacion solicitud) {
        DestinoRemoto destino = mapeador.mapear(solicitud.operacion());
        IntegrationOperation operacion = new IntegrationOperation(
                solicitud.idempotencyKey(),
                solicitud.correlationId(),
                destino.entityType(),
                solicitud.entityId(),
                solicitud.operacion(),
                destino.accion(),
                solicitud.reasonCode(),
                solicitud.effectiveDate(),
                aJson(solicitud),
                properties.integracion().maxIntentos(),
                clock.instant());
        IntegrationOperation guardada = repository.save(operacion);
        // Se consume solo tras el commit (AFTER_COMMIT): nunca se envía algo no confirmado localmente.
        eventos.publishEvent(new OperacionRegistradaEvent(guardada.getId()));
        return guardada;
    }

    private String aJson(SolicitudMutacion solicitud) {
        try {
            return objectMapper.writeValueAsString(solicitud.payload());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el payload de " + solicitud.operacion(), e);
        }
    }
}
