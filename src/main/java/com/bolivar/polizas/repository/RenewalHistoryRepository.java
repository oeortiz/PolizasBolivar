package com.bolivar.polizas.repository;

import com.bolivar.polizas.entity.RenewalHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RenewalHistoryRepository extends JpaRepository<RenewalHistory, Long> {

    Optional<RenewalHistory> findByIdempotencyKey(String idempotencyKey);

    List<RenewalHistory> findByPolizaIdOrderByIdAsc(Long polizaId);
}
