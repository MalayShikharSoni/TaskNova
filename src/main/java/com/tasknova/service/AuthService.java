package com.tasknova.service;

import com.tasknova.dto.auth.JwtResponse;
import com.tasknova.dto.auth.LoginRequest;
import com.tasknova.dto.auth.RegisterRequest;
import com.tasknova.entity.User;
import com.tasknova.entity.enums.Role;
import com.tasknova.exception.ResourceNotFoundException;
import com.tasknova.exception.ValidationException;
import com.tasknova.repository.UserRepository;
import com.tasknova.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles user registration and JWT-based login.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository       userRepository;
    private final PasswordEncoder      passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider     jwtTokenProvider;
    private final AuditService         auditService;

    // ────────────────────────────────────────────────────────────────
    //  Register
    // ────────────────────────────────────────────────────────────────

    /**
     * Registers a new user account with the USER role.
     *
     * @throws ValidationException if email or username is already taken
     */
    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ValidationException("An account with this email already exists.");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ValidationException("This username is already taken.");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .active(true)
                .build();

        userRepository.save(user);
        log.info("New user registered: {}", request.getEmail());
        auditService.log("SYSTEM", "USER_REGISTERED", "User", null,
                "New registration: " + request.getEmail());
    }

    // ────────────────────────────────────────────────────────────────
    //  Login
    // ────────────────────────────────────────────────────────────────

    /**
     * Authenticates the user and returns a JWT response.
     *
     * @throws BadCredentialsException if credentials are invalid
     * @throws ResourceNotFoundException if user record is missing post-auth
     */
    @Transactional(readOnly = true)
    public JwtResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", request.getEmail()));

        String roleStr = "ROLE_" + user.getRole().name();
        String token   = jwtTokenProvider.generateToken(request.getEmail(), roleStr, user.getId());
        long   expiry  = jwtTokenProvider.getExpirationEpoch(token);

        log.info("User logged in: {}", request.getEmail());
        auditService.log(user.getEmail(), "USER_LOGIN", "User", user.getId(), "Login successful");

        return JwtResponse.builder()
                .token(token)
                .type("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .expiresAt(expiry)
                .build();
    }
}
