package com.bolivar.polizas.repository;

import com.bolivar.polizas.entity.IntegrationOperation;
import com.bolivar.polizas.entity.enums.EstadoIntegracion;
import com.bolivar.polizas.entity.enums.OperacionNegocio;
import com.bolivar.polizas.entity.enums.TipoEntidad;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IntegrationOperationRepository extends JpaRepository<IntegrationOperation, UUID> {

    Optional<IntegrationOperation> findByIdempotencyKey(String idempotencyKey);

    /**
     * Reserva operaciones PENDIENTE vencidas. {@code FOR UPDATE SKIP LOCKED} permite que varias
     * instancias procesen en paralelo sin tomar la misma operación.
     */
    @Query(value = """
            select * from integration_operation
            where estado = 'PENDIENTE' and proximo_intento_at <= :ahora
            order by proximo_intento_at
            limit :limite
            for update skip locked
            """, nativeQuery = true)
    List<IntegrationOperation> reservarPendientes(@Param("ahora") Instant ahora, @Param("limite") int limite);

    /** Reserva una operación concreta si está PENDIENTE y vencida (disparo tras commit). */
    @Query(value = """
            select * from integration_operation
            where id = :id and estado = 'PENDIENTE' and proximo_intento_at <= :ahora
            for update skip locked
            """, nativeQuery = true)
    Optional<IntegrationOperation> reservarPendiente(@Param("id") UUID id, @Param("ahora") Instant ahora);

    /** Operaciones EN_PROCESO sin actividad desde {@code limite}: el proceso que las tomó se interrumpió. */
    @Query(value = """
            select * from integration_operation
            where estado = 'EN_PROCESO' and ultimo_intento_at < :limite
            order by ultimo_intento_at
            for update skip locked
            """, nativeQuery = true)
    List<IntegrationOperation> reservarInterrumpidas(@Param("limite") Instant limite);

    List<IntegrationOperation> findByEntityTypeAndEntityId(TipoEntidad entityType, Long entityId);

    long countByEstado(EstadoIntegracion estado);

    /** ¿Hay alguna operación de este tipo sobre la entidad que no esté en el estado indicado? */
    boolean existsByEntityTypeAndEntityIdAndOperacionNegocioAndEstadoNot(
            TipoEntidad entityType, Long entityId, OperacionNegocio operacionNegocio, EstadoIntegracion estado);
}
