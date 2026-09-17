package com.tasknova.repository;

import com.tasknova.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Data access layer for {@link AuditLog} entries.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByActorUsername(String actorUsername, Pageable pageable);

    Page<AuditLog> findAllByTargetType(String targetType, Pageable pageable);

    Page<AuditLog> findAllByAction(String action, Pageable pageable);

    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);
}
