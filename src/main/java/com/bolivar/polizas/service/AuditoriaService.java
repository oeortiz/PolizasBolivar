package com.bolivar.polizas.service;

import com.bolivar.polizas.entity.AuditLog;
import com.bolivar.polizas.entity.enums.ResultadoAuditoria;
import com.bolivar.polizas.entity.enums.TipoEntidad;
import com.bolivar.polizas.exception.DominioException;
import com.bolivar.polizas.repository.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Bitácora de operaciones sobre pólizas y riesgos.
 *
 * <ul>
 *   <li>{@link #registrar}: dentro de la transacción del caso de uso; si este hace rollback,
 *       el registro tampoco queda (no se audita un cambio que no ocurrió).</li>
 *   <li>{@link #registrarRechazo}: en transacción propia, para que el intento rechazado
 *       quede registrado aunque la transacción principal se revierta. Quien la invoca
 *       debe capturar sus fallos para no ocultar el error de negocio original.</li>
 * </ul>
 * El detalle nunca incluye documentos completos de identidad.
 */
@Service
public class AuditoriaService {

    private final AuditLogRepository repository;
    private final ContextoSolicitud contexto;
    private final ObjectMapper objectMapper;

    public AuditoriaService(AuditLogRepository repository, ContextoSolicitud contexto, ObjectMapper objectMapper) {
        this.repository = repository;
        this.contexto = contexto;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(String accion, TipoEntidad entidad, Long entidadId, ResultadoAuditoria resultado,
                          UUID operacionId, Map<String, ?> detalle) {
        repository.save(new AuditLog(contexto.actor(), contexto.canal(), accion, entidad, entidadId,
                resultado, contexto.correlationId(), operacionId, json(detalle), null));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarRechazo(String accion, TipoEntidad entidad, Long entidadId, DominioException error) {
        repository.save(new AuditLog(contexto.actor(), contexto.canal(), accion, entidad, entidadId,
                ResultadoAuditoria.RECHAZADO, contexto.correlationId(), null,
                json(Map.of("codigo", error.getCodigo().name())), error.getMessage()));
    }

    private String json(Map<String, ?> detalle) {
        if (detalle == null || detalle.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(detalle);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el detalle de auditoría", e);
        }
    }
}
