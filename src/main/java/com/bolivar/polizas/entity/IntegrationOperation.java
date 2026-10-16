package com.bolivar.polizas.entity;

import com.bolivar.polizas.entity.enums.AccionRemota;
import com.bolivar.polizas.entity.enums.AdaptadorIntegracion;
import com.bolivar.polizas.entity.enums.EstadoIntegracion;
import com.bolivar.polizas.entity.enums.OperacionNegocio;
import com.bolivar.polizas.entity.enums.TipoEntidad;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Mutación pendiente de sincronizar con el CORE (patrón Transactional Outbox).
 *
 * <p>Se persiste en la misma transacción local que el cambio de dominio, antes de
 * cualquier intento remoto. El {@code id} se genera en la aplicación, es el
 * identificador idempotente de la mutación y no cambia entre reintentos; el
 * {@code correlationId} tampoco.</p>
 *
 * <p>Las transiciones (iniciar intento, confirmar, rechazar, reprogramar, agotar
 * reintentos) se añaden en la Etapa 6. La base de datos impide marcarla como
 * CONFIRMADA sin fecha de confirmación, resultado y adaptador registrados.</p>
 */
@Entity
@Table(name = "integration_operation")
public class IntegrationOperation extends AuditableEntity {

    /** 1 intento inicial + 3 reintentos (supuesto S-08). Configurable por operación. */
    public static final int MAX_INTENTOS_POR_DEFECTO = 4;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "idempotency_key", length = 100, nullable = false, updatable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "correlation_id", nullable = false, updatable = false)
    private UUID correlationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", length = 10, nullable = false, updatable = false)
    private TipoEntidad entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operacion_negocio", length = 30, nullable = false, updatable = false)
    private OperacionNegocio operacionNegocio;

    @Enumerated(EnumType.STRING)
    @Column(name = "accion_remota", length = 10, nullable = false, updatable = false)
    private AccionRemota accionRemota;

    @Column(name = "reason_code", length = 50, updatable = false)
    private String reasonCode;

    @Column(name = "effective_date", nullable = false, updatable = false)
    private LocalDate effectiveDate;

    /** Cuerpo exacto de la mutación; cada reintento reenvía este mismo contenido. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 20, nullable = false)
    private EstadoIntegracion estado;

    @Column(name = "intentos", nullable = false)
    private int intentos;

    @Column(name = "max_intentos", nullable = false, updatable = false)
    private int maxIntentos;

    @Column(name = "proximo_intento_at")
    private Instant proximoIntentoAt;

    @Column(name = "ultimo_intento_at")
    private Instant ultimoIntentoAt;

    @Column(name = "confirmada_at")
    private Instant confirmadaAt;

    @Column(name = "ultimo_error", length = 2000)
    private String ultimoError;

    @Column(name = "ultimo_http_status")
    private Integer ultimoHttpStatus;

    /** Respuesta conocida del CORE (JSON). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resultado")
    private String resultado;

    @Enumerated(EnumType.STRING)
    @Column(name = "adaptador", length = 20)
    private AdaptadorIntegracion adaptador;

    protected IntegrationOperation() {
        // Requerido por JPA
    }

    public IntegrationOperation(String idempotencyKey,
                                UUID correlationId,
                                TipoEntidad entityType,
                                Long entityId,
                                OperacionNegocio operacionNegocio,
                                AccionRemota accionRemota,
                                String reasonCode,
                                LocalDate effectiveDate,
                                String payloadJson,
                                int maxIntentos,
                                Instant disponibleDesde) {
        if (maxIntentos < 1) {
            throw new IllegalArgumentException("maxIntentos debe ser al menos 1");
        }
        this.id = UUID.randomUUID();
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey es obligatoria");
        this.correlationId = Objects.requireNonNull(correlationId, "correlationId es obligatorio");
        this.entityType = Objects.requireNonNull(entityType, "entityType es obligatorio");
        this.entityId = Objects.requireNonNull(entityId, "entityId es obligatorio");
        this.operacionNegocio = Objects.requireNonNull(operacionNegocio, "operacionNegocio es obligatoria");
        this.accionRemota = Objects.requireNonNull(accionRemota, "accionRemota es obligatoria");
        this.reasonCode = reasonCode;
        this.effectiveDate = Objects.requireNonNull(effectiveDate, "effectiveDate es obligatoria");
        this.payload = Objects.requireNonNull(payloadJson, "payload es obligatorio");
        this.maxIntentos = maxIntentos;
        this.estado = EstadoIntegracion.PENDIENTE;
        this.intentos = 0;
        // Primer intento: inmediato según el reloj de negocio inyectado por quien la registra.
        this.proximoIntentoAt = Objects.requireNonNull(disponibleDesde, "disponibleDesde es obligatorio");
    }

    // ------------------------------------------------------------------
    // Transiciones (Etapa 6). La base de datos refuerza las mismas reglas con CHECK.
    // ------------------------------------------------------------------

    /** PENDIENTE → EN_PROCESO, consumiendo un intento. */
    public void iniciarIntento(Instant ahora, AdaptadorIntegracion adaptadorUsado) {
        exigirEstado(EstadoIntegracion.PENDIENTE);
        if (intentos >= maxIntentos) {
            throw new IllegalStateException("La operación " + id + " ya agotó sus " + maxIntentos + " intentos");
        }
        this.estado = EstadoIntegracion.EN_PROCESO;
        this.intentos++;
        this.ultimoIntentoAt = Objects.requireNonNull(ahora);
        this.proximoIntentoAt = null;
        this.adaptador = Objects.requireNonNull(adaptadorUsado);
    }

    /**
     * EN_PROCESO → CONFIRMADA. Solo se invoca con una confirmación válida del adaptador.
     *
     * @param resultadoJson respuesta registrada (JSON válido)
     */
    public void confirmar(Instant ahora, int httpStatus, String resultadoJson) {
        exigirEstado(EstadoIntegracion.EN_PROCESO);
        this.estado = EstadoIntegracion.CONFIRMADA;
        this.confirmadaAt = Objects.requireNonNull(ahora);
        this.ultimoHttpStatus = httpStatus;
        this.resultado = Objects.requireNonNull(resultadoJson);
        this.ultimoError = null;
    }

    /** EN_PROCESO → RECHAZADA: error funcional del CORE, sin reintentos. */
    public void rechazar(Integer httpStatus, String error, String resultadoJson) {
        exigirEstado(EstadoIntegracion.EN_PROCESO);
        this.estado = EstadoIntegracion.RECHAZADA;
        this.ultimoHttpStatus = httpStatus;
        this.ultimoError = recortar(error);
        this.resultado = resultadoJson;
    }

    /** EN_PROCESO → PENDIENTE con un próximo intento programado. */
    public void programarReintento(Instant proximo, Integer httpStatus, String error) {
        exigirEstado(EstadoIntegracion.EN_PROCESO);
        if (!puedeReintentar()) {
            throw new IllegalStateException("La operación " + id + " no tiene reintentos disponibles");
        }
        this.estado = EstadoIntegracion.PENDIENTE;
        this.proximoIntentoAt = Objects.requireNonNull(proximo);
        this.ultimoHttpStatus = httpStatus;
        this.ultimoError = recortar(error);
    }

    /** EN_PROCESO → FALLIDA: reintentos agotados o resultado que exige revisión. Nunca se marca sincronizada. */
    public void marcarFallida(Integer httpStatus, String error) {
        exigirEstado(EstadoIntegracion.EN_PROCESO);
        this.estado = EstadoIntegracion.FALLIDA;
        this.proximoIntentoAt = null;
        this.ultimoHttpStatus = httpStatus;
        this.ultimoError = recortar(error);
    }

    /**
     * Reapertura manual de una operación FALLIDA tras revisarla: vuelve a PENDIENTE con un
     * nuevo presupuesto de intentos. Conserva el último error como referencia.
     */
    public void reabrir(Instant ahora) {
        exigirEstado(EstadoIntegracion.FALLIDA);
        this.estado = EstadoIntegracion.PENDIENTE;
        this.intentos = 0;
        this.proximoIntentoAt = Objects.requireNonNull(ahora);
    }

    public boolean puedeReintentar() {
        return intentos < maxIntentos;
    }

    private void exigirEstado(EstadoIntegracion esperado) {
        if (estado != esperado) {
            throw new IllegalStateException("La operación " + id + " está en " + estado + ", se esperaba " + esperado);
        }
    }

    private static String recortar(String texto) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= 2000 ? texto : texto.substring(0, 1997) + "...";
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public TipoEntidad getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public OperacionNegocio getOperacionNegocio() {
        return operacionNegocio;
    }

    public AccionRemota getAccionRemota() {
        return accionRemota;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public String getPayload() {
        return payload;
    }

    public EstadoIntegracion getEstado() {
        return estado;
    }

    public int getIntentos() {
        return intentos;
    }

    public int getMaxIntentos() {
        return maxIntentos;
    }

    public Instant getProximoIntentoAt() {
        return proximoIntentoAt;
    }

    public Instant getUltimoIntentoAt() {
        return ultimoIntentoAt;
    }

    public Instant getConfirmadaAt() {
        return confirmadaAt;
    }

    public String getUltimoError() {
        return ultimoError;
    }

    public Integer getUltimoHttpStatus() {
        return ultimoHttpStatus;
    }

    public String getResultado() {
        return resultado;
    }

    public AdaptadorIntegracion getAdaptador() {
        return adaptador;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IntegrationOperation other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return IntegrationOperation.class.hashCode();
    }

    @Override
    public String toString() {
        return "IntegrationOperation[id=" + id + ", " + operacionNegocio + "->" + accionRemota
                + ", estado=" + estado + ", intentos=" + intentos + "/" + maxIntentos + "]";
    }
}
