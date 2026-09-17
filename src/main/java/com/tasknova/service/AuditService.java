package com.tasknova.service;

import com.tasknova.entity.AuditLog;
import com.tasknova.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Persists audit events asynchronously so they never block
 * the main business transaction.
 *
 * <p>Called directly by services and also by {@code AuditAspect}
 * for methods annotated with {@code @Auditable}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Persists an audit entry asynchronously in a new transaction.
     *
     * @param actorUsername  who performed the action
     * @param action         the action label e.g. "TASK_CREATED"
     * @param targetType     the affected entity type e.g. "Task"
     * @param targetId       the affected entity's database ID (nullable)
     * @param details        human-readable description
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String actorUsername, String action,
                    String targetType, Long targetId, String details) {
        try {
            AuditLog entry = AuditLog.builder()
                    .actorUsername(actorUsername != null ? actorUsername : "SYSTEM")
                    .action(action)
                    .targetType(targetType)
                    .targetId(targetId)
                    .timestamp(LocalDateTime.now())
                    .details(details)
                    .build();

            auditLogRepository.save(entry);
            log.debug("Audit logged — actor={}, action={}, target={}#{}", 
                      actorUsername, action, targetType, targetId);
        } catch (Exception ex) {
            // Audit failure must never crash the main flow
            log.error("Failed to persist audit log: action={}, error={}", action, ex.getMessage());
        }
    }
}
