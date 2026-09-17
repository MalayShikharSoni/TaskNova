package com.tasknova.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * Cross-cutting concern: structured logging for all service-layer methods.
 *
 * <p>Logs method entry (with arguments), exit (with return value), and
 * execution duration on every public method in the {@code service} package.
 * Exceptions are re-thrown after logging so the caller's exception handling
 * is never bypassed.
 */
@Aspect
@Component
@Slf4j
public class LoggingAspect {

    // ────────────────────────────────────────────────────────────────
    //  Pointcuts
    // ────────────────────────────────────────────────────────────────

    /** All public methods in the service package */
    @Pointcut("execution(public * com.tasknova.service..*(..))")
    public void serviceLayer() {}

    /** All public methods in the controller package */
    @Pointcut("execution(public * com.tasknova.controller..*(..))")
    public void controllerLayer() {}

    // ────────────────────────────────────────────────────────────────
    //  Advice
    // ────────────────────────────────────────────────────────────────

    /**
     * Around advice that logs entry, exit and timing for every service method.
     */
    @Around("serviceLayer()")
    public Object logServiceMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature  = (MethodSignature) joinPoint.getSignature();
        String          className  = signature.getDeclaringType().getSimpleName();
        String          methodName = signature.getName();
        Object[]        args       = joinPoint.getArgs();

        log.debug("→ {}.{}() called with {} arg(s)", className, methodName, args.length);

        long start = System.currentTimeMillis();
        try {
            Object result    = joinPoint.proceed();
            long   elapsed   = System.currentTimeMillis() - start;
            log.debug("← {}.{}() completed in {}ms", className, methodName, elapsed);
            return result;
        } catch (Exception ex) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("✗ {}.{}() threw {} after {}ms — {}",
                    className, methodName,
                    ex.getClass().getSimpleName(), elapsed,
                    ex.getMessage());
            throw ex;
        }
    }

    /**
     * Around advice that logs slow controller calls (> 500ms) as warnings.
     */
    @Around("controllerLayer()")
    public Object logControllerMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature  = (MethodSignature) joinPoint.getSignature();
        String          className  = signature.getDeclaringType().getSimpleName();
        String          methodName = signature.getName();

        long start  = System.currentTimeMillis();
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Exception ex) {
            log.warn("Controller error in {}.{}(): {}", className, methodName, ex.getMessage());
            throw ex;
        }

        long elapsed = System.currentTimeMillis() - start;
        if (elapsed > 500) {
            log.warn("⚠ SLOW REQUEST — {}.{}() took {}ms", className, methodName, elapsed);
        } else {
            log.debug("Controller {}.{}() → {}ms", className, methodName, elapsed);
        }
        return result;
    }
}
