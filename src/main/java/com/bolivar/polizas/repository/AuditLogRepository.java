package com.bolivar.polizas.repository;

import com.bolivar.polizas.entity.AuditLog;
import com.bolivar.polizas.entity.enums.TipoEntidad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByEntityTypeAndEntityIdOrderByIdAsc(TipoEntidad entityType, Long entityId);
}
