package com.tasknova.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.ui.Model;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralised exception handling for the entire application.
 *
 * <p>Handles two response formats:
 * <ul>
 *   <li><strong>REST API</strong> ({@code /api/**}) — returns structured JSON error body</li>
 *   <li><strong>Thymeleaf pages</strong> — redirects to a styled error page</li>
 * </ul>
 *
 * <p>Detection is based on the request URI prefix.
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ────────────────────────────────────────────────────────────────
    //  Resource Not Found — 404
    // ────────────────────────────────────────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public Object handleResourceNotFound(ResourceNotFoundException ex,
                                          HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(errorBody(HttpStatus.NOT_FOUND, ex.getMessage(), request));
        }
        return errorPage("error/404", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    // ────────────────────────────────────────────────────────────────
    //  Unauthorized / Forbidden — 403
    // ────────────────────────────────────────────────────────────────

    @ExceptionHandler(UnauthorizedException.class)
    public Object handleUnauthorized(UnauthorizedException ex,
                                      HttpServletRequest request) {
        log.warn("Unauthorized access: {}", ex.getMessage());
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(errorBody(HttpStatus.FORBIDDEN, ex.getMessage(), request));
        }
        return errorPage("error/403", ex.getMessage(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDenied(AccessDeniedException ex,
                                      HttpServletRequest request) {
        log.warn("Access denied: {}", ex.getMessage());
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(errorBody(HttpStatus.FORBIDDEN,
                            "You do not have permission to perform this action.", request));
        }
        return errorPage("error/403",
                "You do not have permission to access this page.", HttpStatus.FORBIDDEN);
    }

    // ────────────────────────────────────────────────────────────────
    //  Validation — 400
    // ────────────────────────────────────────────────────────────────

    @ExceptionHandler(ValidationException.class)
    public Object handleValidation(ValidationException ex,
                                    HttpServletRequest request) {
        log.warn("Validation error: {}", ex.getMessage());
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(errorBody(HttpStatus.BAD_REQUEST, ex.getMessage(), request));
        }
        return errorPage("error/400", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles {@code @Valid} / {@code @Validated} annotation failures.
     * Collects all field errors into a map.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                HttpServletRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field   = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            fieldErrors.put(field, message);
        });

        log.warn("Bean validation failed: {}", fieldErrors);

        if (isApiRequest(request)) {
            Map<String, Object> body = errorBody(HttpStatus.BAD_REQUEST,
                    "Validation failed", request);
            body.put("fieldErrors", fieldErrors);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        }
        // For page requests, show the first error message
        String firstMessage = fieldErrors.values().stream().findFirst()
                .orElse("Invalid input provided.");
        return errorPage("error/400", firstMessage, HttpStatus.BAD_REQUEST);
    }

    // ────────────────────────────────────────────────────────────────
    //  Authentication failures — 401
    // ────────────────────────────────────────────────────────────────

    @ExceptionHandler({BadCredentialsException.class})
    public Object handleBadCredentials(BadCredentialsException ex,
                                        HttpServletRequest request) {
        log.warn("Bad credentials attempt from: {}", request.getRemoteAddr());
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorBody(HttpStatus.UNAUTHORIZED,
                            "Invalid email or password.", request));
        }
        return "redirect:/auth/login?error=true";
    }

    @ExceptionHandler(DisabledException.class)
    public Object handleDisabledAccount(DisabledException ex,
                                         HttpServletRequest request) {
        log.warn("Login attempt on disabled account");
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorBody(HttpStatus.UNAUTHORIZED,
                            "Your account has been deactivated. Please contact support.", request));
        }
        return "redirect:/auth/login?disabled=true";
    }

    // ────────────────────────────────────────────────────────────────
    //  Catch-all — 500
    // ────────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public Object handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at [{}]: {}", request.getRequestURI(), ex.getMessage(), ex);
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorBody(HttpStatus.INTERNAL_SERVER_ERROR,
                            "An unexpected error occurred. Please try again later.", request));
        }
        return errorPage("error/500",
                "An unexpected error occurred. Please try again later.",
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ────────────────────────────────────────────────────────────────
    //  Helpers
    // ────────────────────────────────────────────────────────────────

    /** Returns true if the request targets the REST API (/api/**) */
    private boolean isApiRequest(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/");
    }

    /** Builds the standard JSON error response body */
    private Map<String, Object> errorBody(HttpStatus status, String message,
                                           HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status",    status.value());
        body.put("error",     status.getReasonPhrase());
        body.put("message",   message);
        body.put("path",      request.getRequestURI());
        return body;
    }

    /** Returns a Thymeleaf ModelAndView pointing to an error template */
    private ModelAndView errorPage(String viewName, String message, HttpStatus status) {
        ModelAndView mav = new ModelAndView(viewName);
        mav.addObject("errorMessage", message);
        mav.addObject("statusCode",   status.value());
        mav.addObject("statusText",   status.getReasonPhrase());
        mav.setStatus(status);
        return mav;
    }
}
