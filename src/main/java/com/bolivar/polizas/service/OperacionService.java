package com.bolivar.polizas.service;

import com.bolivar.polizas.entity.IntegrationOperation;
import com.bolivar.polizas.entity.enums.EstadoIntegracion;
import com.bolivar.polizas.entity.enums.ResultadoAuditoria;
import com.bolivar.polizas.exception.CodigoError;
import com.bolivar.polizas.exception.RecursoNoEncontradoException;
import com.bolivar.polizas.exception.ReglaNegocioException;
import com.bolivar.polizas.integration.outbox.OperacionRegistradaEvent;
import com.bolivar.polizas.integration.outbox.SincronizacionEntidades;
import com.bolivar.polizas.repository.IntegrationOperationRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/** Consulta y reapertura manual de operaciones de integración (GET/POST /operaciones/{id}). */
@Service
public class OperacionService {

    private final IntegrationOperationRepository repository;
    private final SincronizacionEntidades sincronizacion;
    private final AuditoriaService auditoria;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    public OperacionService(IntegrationOperationRepository repository, SincronizacionEntidades sincronizacion,
                            AuditoriaService auditoria, ApplicationEventPublisher eventos, Clock clock) {
        this.repository = repository;
        this.sincronizacion = sincronizacion;
        this.auditoria = auditoria;
        this.eventos = eventos;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public IntegrationOperation obtener(UUID id) {
        return repository.findById(id).orElseThrow(() -> RecursoNoEncontradoException.operacion(id));
    }

    /**
     * Reabre una operación FALLIDA tras revisarla (p. ej. el CORE volvió a estar disponible):
     * vuelve a PENDIENTE con un nuevo presupuesto de intentos y la misma clave idempotente.
     * Una RECHAZADA no se reabre: el CORE la rechazó por un motivo funcional que hay que corregir.
     */
    @Transactional
    public IntegrationOperation reintentar(UUID id) {
        IntegrationOperation op = obtener(id);
        if (op.getEstado() != EstadoIntegracion.FALLIDA) {
            throw new ReglaNegocioException(CodigoError.OPERACION_NO_REINTENTABLE,
                    "Solo se puede reintentar una operación FALLIDA; estado actual: " + op.getEstado());
        }
        op.reabrir(clock.instant());
        sincronizacion.actualizar(op);
        auditoria.registrar("INTEGRACION_REINTENTO_MANUAL", op.getEntityType(), op.getEntityId(),
                ResultadoAuditoria.ACEPTADO_PENDIENTE, op.getId(),
                Map.of("operacionNegocio", op.getOperacionNegocio().name(),
                        "ultimoError", op.getUltimoError() == null ? "" : op.getUltimoError()));
        eventos.publishEvent(new OperacionRegistradaEvent(op.getId()));
        return op;
    }
}
