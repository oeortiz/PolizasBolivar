package com.bolivar.polizas.entity;

import com.bolivar.polizas.entity.enums.ResultadoAuditoria;
import com.bolivar.polizas.entity.enums.TipoEntidad;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro inmutable de auditoría: quién (actor, canal), cuándo, qué acción sobre
 * qué entidad, con qué resultado y con qué identificadores de trazabilidad.
 * {@code detalle} no debe contener secretos ni datos personales sin enmascarar.
 */
@Entity
@Immutable
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor", length = 100, nullable = false, updatable = false)
    private String actor;

    @Column(name = "canal", length = 50, updatable = false)
    private String canal;

    /** Acción auditada, p. ej. RENOVAR_POLIZA o INTEGRACION_CONFIRMADA. */
    @Column(name = "accion", length = 50, nullable = false, updatable = false)
    private String accion;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", length = 10, nullable = false, updatable = false)
    private TipoEntidad entityType;

    @Column(name = "entity_id", updatable = false)
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resultado", length = 20, nullable = false, updatable = false)
    private ResultadoAuditoria resultado;

    @Column(name = "correlation_id", updatable = false)
    private UUID correlationId;

    @Column(name = "integration_operation_id", updatable = false)
    private UUID integrationOperationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detalle", updatable = false)
    private String detalle;

    @Column(name = "mensaje_error", length = 2000, updatable = false)
    private String mensajeError;

    protected AuditLog() {
        // Requerido por JPA
    }

    public AuditLog(String actor,
                    String canal,
                    String accion,
                    TipoEntidad entityType,
                    Long entityId,
                    ResultadoAuditoria resultado,
                    UUID correlationId,
                    UUID integrationOperationId,
                    String detalleJson,
                    String mensajeError) {
        this.actor = Objects.requireNonNull(actor, "actor es obligatorio");
        this.canal = canal;
        this.accion = Objects.requireNonNull(accion, "accion es obligatoria");
        this.entityType = Objects.requireNonNull(entityType, "entityType es obligatorio");
        this.entityId = entityId;
        this.resultado = Objects.requireNonNull(resultado, "resultado es obligatorio");
        this.correlationId = correlationId;
        this.integrationOperationId = integrationOperationId;
        this.detalle = detalleJson;
        this.mensajeError = mensajeError;
    }

    @PrePersist
    void onCreate() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getActor() {
        return actor;
    }

    public String getCanal() {
        return canal;
    }

    public String getAccion() {
        return accion;
    }

    public TipoEntidad getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public ResultadoAuditoria getResultado() {
        return resultado;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public UUID getIntegrationOperationId() {
        return integrationOperationId;
    }

    public String getDetalle() {
        return detalle;
    }

    public String getMensajeError() {
        return mensajeError;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuditLog other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return AuditLog.class.hashCode();
    }
}
