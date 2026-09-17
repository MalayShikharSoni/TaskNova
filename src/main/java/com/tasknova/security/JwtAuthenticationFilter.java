package com.tasknova.security;

import com.tasknova.config.JwtConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Intercepts every HTTP request and extracts a JWT from the
 * {@code Authorization: Bearer <token>} header.
 *
 * <p>If the token is valid, it populates the Spring Security
 * {@link SecurityContextHolder} so downstream filters and
 * controllers can access the authenticated principal.
 *
 * <p>This filter only activates for {@code /api/**} endpoints.
 * Thymeleaf page security is handled by the session-based form login.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    // ────────────────────────────────────────────────────────────────
    //  Skip non-API routes to avoid interfering with session auth
    // ────────────────────────────────────────────────────────────────
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.startsWith("/api/");
    }

    // ────────────────────────────────────────────────────────────────
    //  Core filter logic
    // ────────────────────────────────────────────────────────────────
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         filterChain
    ) throws ServletException, IOException {

        try {
            String jwt = extractTokenFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                String email  = tokenProvider.getEmailFromToken(jwt);
                String roles  = tokenProvider.getRolesFromToken(jwt);
                Long   userId = tokenProvider.getUserIdFromToken(jwt);

                // Build authorities from roles string e.g. "ROLE_USER,ROLE_ADMIN"
                List<SimpleGrantedAuthority> authorities = Arrays.stream(roles.split(","))
                        .filter(StringUtils::hasText)
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(email, null, authorities);

                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // Store userId as a request attribute for downstream use
                request.setAttribute("authenticatedUserId", userId);

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("JWT authentication set for user: {}", email);
            }
        } catch (Exception ex) {
            log.error("Could not set JWT authentication in security context: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    // ────────────────────────────────────────────────────────────────
    //  Helper: strip "Bearer " prefix
    // ────────────────────────────────────────────────────────────────
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(JwtConfig.HEADER_NAME);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(JwtConfig.TOKEN_PREFIX)) {
            return bearerToken.substring(JwtConfig.TOKEN_PREFIX.length());
        }
        return null;
    }
}
