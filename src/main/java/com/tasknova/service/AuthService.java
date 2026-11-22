package com.tasknova.service;

import com.tasknova.dto.auth.JwtResponse;
import com.tasknova.dto.auth.LoginRequest;
import com.tasknova.dto.auth.RegisterRequest;
import com.tasknova.entity.User;
import com.tasknova.entity.enums.Role;
import com.tasknova.exception.ResourceNotFoundException;
import com.tasknova.exception.ValidationException;
import com.tasknova.repository.UserRepository;
import com.tasknova.security.JwtCookieService;
import com.tasknova.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Handles user registration and JWT-based login.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository          userRepository;
    private final PasswordEncoder         passwordEncoder;
    private final AuthenticationManager    authenticationManager;
    private final JwtTokenProvider        jwtTokenProvider;
    private final JwtCookieService        jwtCookieService;
    private final AuditService            auditService;

    private final HttpSessionSecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

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
        String email    = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String username = request.getUsername().trim();

        // Login matches email/username case-insensitively, so uniqueness checks must too —
        // otherwise "Admin" and "admin" would make sign-in ambiguous.
        if (userRepository.findByEmailOrUsername(email).isPresent()) {
            throw new ValidationException("An account with this email already exists.");
        }
        if (userRepository.findByEmailOrUsername(username).isPresent()) {
            throw new ValidationException("This username is already taken.");
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .active(true)
                .build();

        User saved = userRepository.save(user);
        log.info("New user registered: {}", email);
        auditService.log(email, "USER_REGISTERED", "User", saved.getId(),
                "New registration: " + username);
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
        return login(request, null, null);
    }

    @Transactional(readOnly = true)
    public JwtResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String identifier = request.getEmail().trim();
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(identifier, request.getPassword())
        );

        User user = userRepository.findByEmailOrUsername(identifier)
                .orElseThrow(() -> new ResourceNotFoundException("User", "identifier", identifier));

        String roleStr = "ROLE_" + user.getRole().name();
        String token   = jwtTokenProvider.generateToken(user.getEmail(), roleStr, user.getId());
        long   expiry  = jwtTokenProvider.getExpirationEpoch(token);

        if (httpRequest != null && httpResponse != null) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, httpRequest, httpResponse);

            jwtCookieService.write(token, httpRequest, httpResponse);
        }

        log.info("User logged in: {}", user.getEmail());
        auditService.log(user.getEmail(), "USER_LOGIN", "User", user.getId(), "Login successful");

        return JwtResponse.builder()
                .token(token)
                .type("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(roleStr)
                .expiresAt(expiry)
                .build();
    }
}
