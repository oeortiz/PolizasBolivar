package com.bolivar.polizas.entity;

import com.bolivar.polizas.entity.enums.EstadoRiesgo;
import com.bolivar.polizas.entity.enums.EstadoSincronizacion;
import com.bolivar.polizas.exception.CodigoError;
import com.bolivar.polizas.exception.ReglaNegocioException;
import com.bolivar.polizas.exception.ValorInvalidoException;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Riesgo (inmueble arrendado) asegurado dentro de una póliza.
 * No tiene vigencia propia: su prima se calcula con la vigencia de la póliza.
 */
@Entity
@Table(name = "riesgo")
public class Riesgo extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "poliza_id", nullable = false, updatable = false)
    private Poliza poliza;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 20, nullable = false)
    private EstadoRiesgo estado;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_sincronizacion", length = 20, nullable = false)
    private EstadoSincronizacion estadoSincronizacion;

    @Column(name = "canon_mensual", precision = 15, scale = 2, nullable = false)
    private BigDecimal canonMensual;

    @Column(name = "prima", precision = 17, scale = 2, nullable = false)
    private BigDecimal prima;

    @Column(name = "direccion_inmueble", length = 250, nullable = false)
    private String direccionInmueble;

    /** Asegurado. */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "tipoDocumento",   column = @Column(name = "arrendatario_tipo_documento")),
            @AttributeOverride(name = "numeroDocumento", column = @Column(name = "arrendatario_numero_documento")),
            @AttributeOverride(name = "nombre",          column = @Column(name = "arrendatario_nombre"))
    })
    private Participante arrendatario;

    /** Beneficiario. */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "tipoDocumento",   column = @Column(name = "arrendador_tipo_documento")),
            @AttributeOverride(name = "numeroDocumento", column = @Column(name = "arrendador_numero_documento")),
            @AttributeOverride(name = "nombre",          column = @Column(name = "arrendador_nombre"))
    })
    private Participante arrendador;

    @Column(name = "fecha_cancelacion")
    private Instant fechaCancelacion;

    @Column(name = "motivo_cancelacion", length = 250)
    private String motivoCancelacion;

    protected Riesgo() {
        // Requerido por JPA
    }

    /** Solo lo invoca {@link Poliza#agregarRiesgo}, que calcula la prima y la consolidación. */
    Riesgo(Poliza poliza,
           String direccionInmueble,
           Participante arrendatario,
           Participante arrendador,
           BigDecimal canonMensual,
           BigDecimal prima) {
        this.poliza = Objects.requireNonNull(poliza, "poliza es obligatoria");
        if (direccionInmueble == null || direccionInmueble.isBlank()) {
            throw new ValorInvalidoException("La dirección del inmueble es obligatoria");
        }
        if (arrendatario == null) {
            throw new ValorInvalidoException("El arrendatario es obligatorio");
        }
        if (arrendador == null) {
            throw new ValorInvalidoException("El arrendador es obligatorio");
        }
        this.direccionInmueble = direccionInmueble.trim();
        this.arrendatario = arrendatario;
        this.arrendador = arrendador;
        this.canonMensual = Objects.requireNonNull(canonMensual, "canonMensual es obligatorio");
        this.prima = Objects.requireNonNull(prima, "prima es obligatoria");
        this.estado = EstadoRiesgo.ACTIVO;
        this.estadoSincronizacion = EstadoSincronizacion.NO_REQUERIDA;
    }

    /** Las reglas (pertenencia, tipo de póliza, último activo) las valida {@link Poliza#cancelarRiesgo}. */
    void cancelar(Instant fecha, String motivo) {
        if (estado == EstadoRiesgo.CANCELADO) {
            throw new ReglaNegocioException(CodigoError.RIESGO_YA_CANCELADO, "El riesgo ya está cancelado");
        }
        if (fecha == null) {
            throw new ValorInvalidoException("La fecha de cancelación es obligatoria");
        }
        this.estado = EstadoRiesgo.CANCELADO;
        this.fechaCancelacion = fecha;
        this.motivoCancelacion = motivo;
    }

    /** La última mutación local está registrada para el CORE y aún no está confirmada. */
    public void marcarSincronizacionPendiente() {
        this.estadoSincronizacion = EstadoSincronizacion.PENDIENTE;
    }

    /** Estado técnico derivado de las operaciones de integración (lo calcula el procesador). */
    public void actualizarEstadoSincronizacion(EstadoSincronizacion nuevo) {
        this.estadoSincronizacion = Objects.requireNonNull(nuevo);
    }

    /** Nuevo canon tras aplicar IPC; la prima la recalcula después el agregado. */
    void actualizarCanon(BigDecimal nuevoCanon) {
        this.canonMensual = Objects.requireNonNull(nuevoCanon, "canonMensual es obligatorio");
    }

    void asignarPrima(BigDecimal nuevaPrima) {
        this.prima = Objects.requireNonNull(nuevaPrima, "prima es obligatoria");
    }

    public boolean isActivo() {
        return estado == EstadoRiesgo.ACTIVO;
    }

    public Long getId() {
        return id;
    }

    public Poliza getPoliza() {
        return poliza;
    }

    public EstadoRiesgo getEstado() {
        return estado;
    }

    public EstadoSincronizacion getEstadoSincronizacion() {
        return estadoSincronizacion;
    }

    public BigDecimal getCanonMensual() {
        return canonMensual;
    }

    public BigDecimal getPrima() {
        return prima;
    }

    public String getDireccionInmueble() {
        return direccionInmueble;
    }

    public Participante getArrendatario() {
        return arrendatario;
    }

    public Participante getArrendador() {
        return arrendador;
    }

    public Instant getFechaCancelacion() {
        return fechaCancelacion;
    }

    public String getMotivoCancelacion() {
        return motivoCancelacion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Riesgo other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Riesgo.class.hashCode();
    }

    @Override
    public String toString() {
        return "Riesgo[id=" + id + ", estado=" + estado + "]";
    }
}
