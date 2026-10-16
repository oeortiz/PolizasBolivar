package com.bolivar.polizas.repository;

import com.bolivar.polizas.entity.Poliza;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PolizaRepository extends JpaRepository<Poliza, Long>, JpaSpecificationExecutor<Poliza> {

    /**
     * Carga la póliza con bloqueo de escritura ({@code SELECT ... FOR UPDATE}). Todas las
     * mutaciones de una póliza o de sus riesgos pasan por aquí: se serializan por póliza, lo
     * que evita consolidaciones inconsistentes y dobles registros ante reintentos concurrentes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Poliza p where p.id = :id")
    Optional<Poliza> findByIdForUpdate(@Param("id") Long id);
}
