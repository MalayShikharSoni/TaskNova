package com.tasknova.controller.api;

import com.tasknova.dto.auth.JwtResponse;
import com.tasknova.dto.auth.LoginRequest;
import com.tasknova.dto.auth.RegisterRequest;
import com.tasknova.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API for authentication.
 * All endpoints are public (no JWT required).
 *
 * <p>POST /api/auth/login    — returns JWT and establishes session
 * <p>POST /api/auth/register — creates account
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthApiController {

    private final AuthService authService;

    /**
     * Authenticate and receive a JWT token.
     * Also establishes the session and cookie for seamless web UI navigation.
     *
     * @param request login credentials (email or username + password)
     * @return 200 with {@link JwtResponse} body
     */
    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        JwtResponse response = authService.login(request, httpRequest, httpResponse);
        return ResponseEntity.ok(response);
    }

    /**
     * Register a new user account.
     *
     * @param request registration details (username, email, password)
     * @return 201 Created with success message
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(
            @Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Map.of("message", "Account created successfully. Please log in."));
    }
}
