package com.bolivar.polizas.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Fotografía inmutable de una renovación: valores anteriores y nuevos, IPC aplicado
 * y operación de integración asociada. La unicidad de {@code idempotencyKey} garantiza
 * que un reintento de la misma solicitud no cree un segundo historial.
 */
@Entity
@Immutable
@Table(name = "renewal_history")
public class RenewalHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "poliza_id", nullable = false, updatable = false)
    private Poliza poliza;

    /** Referencia a {@link IntegrationOperation} (sin asociación JPA para mantener el historial desacoplado). */
    @Column(name = "integration_operation_id", updatable = false)
    private UUID integrationOperationId;

    @Column(name = "idempotency_key", length = 100, nullable = false, updatable = false, unique = true)
    private String idempotencyKey;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "canonMensual",        column = @Column(name = "canon_anterior")),
            @AttributeOverride(name = "prima",               column = @Column(name = "prima_anterior")),
            @AttributeOverride(name = "fechaInicioVigencia", column = @Column(name = "fecha_inicio_vigencia_anterior")),
            @AttributeOverride(name = "fechaFinVigencia",    column = @Column(name = "fecha_fin_vigencia_anterior")),
            @AttributeOverride(name = "estado",              column = @Column(name = "estado_anterior"))
    })
    private FotografiaPoliza anterior;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "canonMensual",        column = @Column(name = "canon_nuevo")),
            @AttributeOverride(name = "prima",               column = @Column(name = "prima_nueva")),
            @AttributeOverride(name = "fechaInicioVigencia", column = @Column(name = "fecha_inicio_vigencia_nueva")),
            @AttributeOverride(name = "fechaFinVigencia",    column = @Column(name = "fecha_fin_vigencia_nueva")),
            @AttributeOverride(name = "estado",              column = @Column(name = "estado_nuevo"))
    })
    private FotografiaPoliza nueva;

    /** Porcentaje aplicado: 5.6000 representa 5,60 %. */
    @Column(name = "ipc_aplicado", precision = 7, scale = 4, nullable = false, updatable = false)
    private BigDecimal ipcAplicado;

    /** Constante de {@code system_constant} de la que se tomó el IPC (trazabilidad). */
    @Column(name = "ipc_constante_nombre", length = 100, nullable = false, updatable = false)
    private String ipcConstanteNombre;

    @Column(name = "duracion_meses", nullable = false, updatable = false)
    private int duracionMeses;

    /** Arreglo JSON con la fotografía por riesgo: riesgoId, canon y prima anteriores/nuevos. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detalle_riesgos", nullable = false, updatable = false)
    private String detalleRiesgos;

    @Column(name = "fecha_renovacion", nullable = false, updatable = false)
    private Instant fechaRenovacion;

    @Column(name = "usuario", length = 100, nullable = false, updatable = false)
    private String usuario;

    protected RenewalHistory() {
        // Requerido por JPA
    }

    public RenewalHistory(Poliza poliza,
                          String idempotencyKey,
                          UUID integrationOperationId,
                          FotografiaPoliza anterior,
                          FotografiaPoliza nueva,
                          BigDecimal ipcAplicado,
                          String ipcConstanteNombre,
                          int duracionMeses,
                          String detalleRiesgosJson,
                          String usuario) {
        this.poliza = Objects.requireNonNull(poliza, "poliza es obligatoria");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey es obligatoria");
        this.integrationOperationId = integrationOperationId;
        this.anterior = Objects.requireNonNull(anterior, "fotografía anterior es obligatoria");
        this.nueva = Objects.requireNonNull(nueva, "fotografía nueva es obligatoria");
        this.ipcAplicado = Objects.requireNonNull(ipcAplicado, "ipcAplicado es obligatorio");
        this.ipcConstanteNombre = Objects.requireNonNull(ipcConstanteNombre, "ipcConstanteNombre es obligatorio");
        this.duracionMeses = duracionMeses;
        this.detalleRiesgos = Objects.requireNonNull(detalleRiesgosJson, "detalleRiesgos es obligatorio");
        this.usuario = Objects.requireNonNull(usuario, "usuario es obligatorio");
    }

    @PrePersist
    void onCreate() {
        if (fechaRenovacion == null) {
            fechaRenovacion = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Poliza getPoliza() {
        return poliza;
    }

    public UUID getIntegrationOperationId() {
        return integrationOperationId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public FotografiaPoliza getAnterior() {
        return anterior;
    }

    public FotografiaPoliza getNueva() {
        return nueva;
    }

    public BigDecimal getIpcAplicado() {
        return ipcAplicado;
    }

    public String getIpcConstanteNombre() {
        return ipcConstanteNombre;
    }

    public int getDuracionMeses() {
        return duracionMeses;
    }

    public String getDetalleRiesgos() {
        return detalleRiesgos;
    }

    public Instant getFechaRenovacion() {
        return fechaRenovacion;
    }

    public String getUsuario() {
        return usuario;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RenewalHistory other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return RenewalHistory.class.hashCode();
    }
}
