package com.tasknova.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralised exception handling for the entire application.
 *
 * <p>Handles two response formats:
 * <ul>
 *   <li><strong>REST API</strong> ({@code /api/**}) — returns a structured JSON error body</li>
 *   <li><strong>Thymeleaf pages</strong> — renders a styled error page</li>
 * </ul>
 *
 * <p>Detection is based on the request URI prefix.
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ────────────────────────────────────────────────────────────────
    //  404
    // ────────────────────────────────────────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public Object handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return respond(request, HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Unknown URLs. Without this, Spring 6.1's NoResourceFoundException would fall
     * through to the catch-all below and every typo'd URL would render as a 500.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        log.debug("No handler or resource for {}", request.getRequestURI());
        return respond(request, HttpStatus.NOT_FOUND,
                "The page you are looking for does not exist or has been moved.");
    }

    // ────────────────────────────────────────────────────────────────
    //  403
    // ────────────────────────────────────────────────────────────────

    @ExceptionHandler(UnauthorizedException.class)
    public Object handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        log.warn("Unauthorized access: {}", ex.getMessage());
        return respond(request, HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied: {}", ex.getMessage());
        return respond(request, HttpStatus.FORBIDDEN,
                "You do not have permission to perform this action.");
    }

    // ────────────────────────────────────────────────────────────────
    //  400
    // ────────────────────────────────────────────────────────────────

    @ExceptionHandler(ValidationException.class)
    public Object handleValidation(ValidationException ex, HttpServletRequest request) {
        log.warn("Validation error: {}", ex.getMessage());
        return respond(request, HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** {@code @Valid} failures — collects field errors into a map. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                               HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = error instanceof FieldError fe ? fe.getField() : error.getObjectName();
            fieldErrors.putIfAbsent(field, error.getDefaultMessage());
        });
        log.warn("Bean validation failed: {}", fieldErrors);

        String firstMessage = fieldErrors.values().stream().findFirst().orElse("Invalid input provided.");
        if (isApiRequest(request)) {
            Map<String, Object> body = errorBody(HttpStatus.BAD_REQUEST, firstMessage, request);
            body.put("fieldErrors", fieldErrors);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        }
        return errorPage(HttpStatus.BAD_REQUEST, firstMessage);
    }

    /** Wrong type in a path/query param, e.g. {@code ?status=FINISHED} or {@code /tasks/abc}. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Object handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'.";
        log.warn(message);
        return respond(request, HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Object handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return respond(request, HttpStatus.BAD_REQUEST, "Missing parameter '" + ex.getParameterName() + "'.");
    }

    /** Malformed JSON, or an unknown enum value inside the JSON body. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Object handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Unreadable request body: {}", ex.getMostSpecificCause().getMessage());
        return respond(request, HttpStatus.BAD_REQUEST,
                "The request body is malformed or contains an invalid value.");
    }

    // ────────────────────────────────────────────────────────────────
    //  405
    // ────────────────────────────────────────────────────────────────

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Object handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                           HttpServletRequest request) {
        return respond(request, HttpStatus.METHOD_NOT_ALLOWED,
                "Method " + ex.getMethod() + " is not supported for this URL.");
    }

    // ────────────────────────────────────────────────────────────────
    //  401
    // ────────────────────────────────────────────────────────────────

    @ExceptionHandler(BadCredentialsException.class)
    public Object handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        log.warn("Bad credentials attempt from: {}", request.getRemoteAddr());
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorBody(HttpStatus.UNAUTHORIZED, "Invalid email/username or password.", request));
        }
        return "redirect:/auth/login?error=true";
    }

    @ExceptionHandler(DisabledException.class)
    public Object handleDisabledAccount(DisabledException ex, HttpServletRequest request) {
        log.warn("Login attempt on disabled account");
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorBody(HttpStatus.UNAUTHORIZED,
                            "Your account has been deactivated. Please contact support.", request));
        }
        return "redirect:/auth/login?disabled=true";
    }

    // ────────────────────────────────────────────────────────────────
    //  500
    // ────────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public Object handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at [{}]: {}", request.getRequestURI(), ex.getMessage(), ex);
        return respond(request, HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.");
    }

    // ────────────────────────────────────────────────────────────────
    //  Helpers
    // ────────────────────────────────────────────────────────────────

    private Object respond(HttpServletRequest request, HttpStatus status, String message) {
        if (isApiRequest(request)) {
            return ResponseEntity.status(status).body(errorBody(status, message, request));
        }
        return errorPage(status, message);
    }

    private boolean isApiRequest(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/");
    }

    private Map<String, Object> errorBody(HttpStatus status, String message, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status",    status.value());
        body.put("error",     status.getReasonPhrase());
        body.put("message",   message);
        body.put("path",      request.getRequestURI());
        return body;
    }

    /** Renders error/{code}.html when one exists, else the generic error.html. */
    private ModelAndView errorPage(HttpStatus status, String message) {
        String view = switch (status.value()) {
            case 400, 403, 404, 500 -> "error/" + status.value();
            default -> "error";
        };
        ModelAndView mav = new ModelAndView(view);
        mav.addObject("errorMessage", message);
        mav.addObject("status",       status.value());
        mav.addObject("statusCode",   status.value());
        mav.addObject("statusText",   status.getReasonPhrase());
        mav.setStatus(status);
        return mav;
    }
}
