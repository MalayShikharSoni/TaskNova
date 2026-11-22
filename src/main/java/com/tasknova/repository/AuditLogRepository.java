package com.tasknova.repository;

import com.tasknova.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);

    /** Partial, case-insensitive match so admins can filter by "demo" or a full email. */
    Page<AuditLog> findAllByActorUsernameContainingIgnoreCase(String actorUsername, Pageable pageable);
}
