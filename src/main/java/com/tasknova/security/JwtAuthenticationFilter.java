package com.tasknova.security;

import com.tasknova.config.JwtConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Intercepts HTTP requests and extracts a JWT from either:
 * 1) {@code Authorization: Bearer <token>} header (REST APIs)
 * 2) {@code tasknova_jwt} Cookie (Web UI page navigations)
 *
 * <p>If the token is valid, it populates the Spring Security
 * {@link SecurityContextHolder} with a {@link UserDetails} principal.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;

    public static final String JWT_COOKIE_NAME = "tasknova_jwt";

    // ────────────────────────────────────────────────────────────────
    //  Skip static asset paths only
    // ────────────────────────────────────────────────────────────────
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/assets/")
                || path.startsWith("/static/")
                || path.equals("/favicon.ico");
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
                Long   userId = tokenProvider.getUserIdFromToken(jwt);

                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // Store userId as a request attribute for downstream controllers
                request.setAttribute("authenticatedUserId", userId);

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("JWT authentication set for user: {}", email);
            }
        } catch (Exception ex) {
            log.warn("Could not set JWT authentication in security context: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    // ────────────────────────────────────────────────────────────────
    //  Helper: extract token from Authorization header or Cookie
    // ────────────────────────────────────────────────────────────────
    private String extractTokenFromRequest(HttpServletRequest request) {
        // 1. Try Authorization header
        String bearerToken = request.getHeader(JwtConfig.HEADER_NAME);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(JwtConfig.TOKEN_PREFIX)) {
            return bearerToken.substring(JwtConfig.TOKEN_PREFIX.length());
        }

        // 2. Try tasknova_jwt cookie
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (JWT_COOKIE_NAME.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                    return cookie.getValue();
                }
            }
        }

        return null;
    }
}
