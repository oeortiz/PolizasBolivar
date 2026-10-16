package com.bolivar.polizas.repository;

import com.bolivar.polizas.entity.Riesgo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RiesgoRepository extends JpaRepository<Riesgo, Long> {

    List<Riesgo> findByPolizaIdOrderByIdAsc(Long polizaId);

    /** Solo el id de la póliza, sin cargar el riesgo, para bloquear primero la póliza. */
    @Query("select r.poliza.id from Riesgo r where r.id = :id")
    Optional<Long> findPolizaIdByRiesgoId(@Param("id") Long riesgoId);
}
