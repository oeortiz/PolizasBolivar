package com.bolivar.polizas.repository;

import com.bolivar.polizas.entity.SystemConstant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SystemConstantRepository extends JpaRepository<SystemConstant, Long> {

    Optional<SystemConstant> findByNombre(String nombre);
}
