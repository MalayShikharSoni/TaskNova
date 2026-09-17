package com.tasknova.aop;

import com.tasknova.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Cross-cutting concern: automatic audit trail for methods
 * annotated with {@link Auditable}.
 *
 * <p>Runs <em>after</em> the method returns successfully. If the method
 * throws an exception the audit entry is intentionally skipped —
 * failed actions are not recorded as completed events.
 *
 * <p>Attempts to extract a {@code Long} entity ID from the method
 * arguments (first {@code Long} parameter found) for the {@code targetId}.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditService auditService;

    // ────────────────────────────────────────────────────────────────
    //  Advice
    // ────────────────────────────────────────────────────────────────

    /**
     * Fires after any method annotated with {@code @Auditable} returns normally.
     *
     * @param joinPoint the intercepted method
     * @param auditable the annotation carrying action metadata
     */
    @AfterReturning(
        pointcut = "@annotation(auditable)",
        returning = "result"
    )
    public void captureAuditEvent(JoinPoint joinPoint, Auditable auditable, Object result) {
        try {
            String actor      = resolveActorUsername();
            String action     = auditable.action();
            String targetType = auditable.targetType();
            Long   targetId   = extractFirstLongArg(joinPoint.getArgs());

            String details = auditable.description().isBlank()
                    ? buildDefaultDetails(joinPoint, action)
                    : auditable.description();

            auditService.log(actor, action, targetType, targetId, details);

        } catch (Exception ex) {
            // Audit aspect must never crash the main application flow
            log.error("AuditAspect failed to record event: {}", ex.getMessage());
        }
    }

    // ────────────────────────────────────────────────────────────────
    //  Helpers
    // ────────────────────────────────────────────────────────────────

    /**
     * Resolves the currently authenticated user's email from the Security context.
     * Falls back to "ANONYMOUS" if no session exists.
     */
    private String resolveActorUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "ANONYMOUS";
        }
        return auth.getName();
    }

    /**
     * Extracts the first {@code Long} argument from the method args array.
     * This typically corresponds to the entity ID (taskId, userId, etc.).
     */
    private Long extractFirstLongArg(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof Long) {
                return (Long) arg;
            }
        }
        return null;
    }

    /**
     * Builds a default detail string: "ClassName.methodName()"
     */
    private String buildDefaultDetails(JoinPoint joinPoint, String action) {
        String className  = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        return action + " triggered by " + className + "." + methodName + "()";
    }
}
