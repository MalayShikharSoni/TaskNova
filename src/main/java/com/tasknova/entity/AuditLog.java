package com.tasknova.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Persisted audit record for significant system actions.
 *
 * <p>Populated by {@code AuditAspect} when methods annotated with
 * {@code @Auditable} are invoked. Provides a tamper-evident trail
 * of who did what and when.
 */
@Entity
@Table(name = "audit_logs",
        indexes = {
            @Index(columnList = "actor_username", name = "idx_audit_actor"),
            @Index(columnList = "timestamp", name = "idx_audit_timestamp")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Username of the user who triggered the action */
    @Column(name = "actor_username", nullable = false, length = 100)
    private String actorUsername;

    /**
     * Descriptive action label.
     * e.g. "TASK_CREATED", "TASK_DELETED", "USER_DEACTIVATED"
     */
    @Column(nullable = false, length = 100)
    private String action;

    /** Entity type affected — e.g. "Task", "User" */
    @Column(name = "target_type", length = 50)
    private String targetType;

    /** ID of the affected entity */
    @Column(name = "target_id")
    private Long targetId;

    /** ISO timestamp of the event */
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /** Human-readable detail about the action */
    @Column(columnDefinition = "TEXT")
    private String details;
}
