package com.tasknova.aop;

import java.lang.annotation.*;

/**
 * Marks a service method for automatic audit logging via {@link AuditAspect}.
 *
 * <p>Usage example:
 * <pre>
 *   &#64;Auditable(action = "TASK_DELETED", targetType = "Task")
 *   public void deleteTask(Long taskId, ...) { ... }
 * </pre>
 *
 * <p>The aspect will capture the method arguments to extract the
 * target entity ID automatically when possible.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auditable {

    /**
     * The action label stored in the audit log.
     * Use SCREAMING_SNAKE_CASE e.g. "TASK_CREATED", "USER_ROLE_CHANGED".
     */
    String action();

    /**
     * The entity type being acted upon e.g. "Task", "User".
     * Stored in the {@code target_type} column.
     */
    String targetType() default "";

    /**
     * Optional human-readable description of the action.
     * Supports SpEL expressions in the future.
     */
    String description() default "";
}
