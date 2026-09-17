package com.tasknova.security;

import com.tasknova.config.JwtConfig;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.stream.Collectors;

/**
 * Handles JWT token generation, parsing, and validation.
 *
 * <p>Uses HMAC-SHA256 (HS256) signing with a secret key sourced
 * from {@link JwtConfig}. Token claims include:
 * <ul>
 *   <li>{@code sub} — user email (login identifier)</li>
 *   <li>{@code roles} — comma-separated role string</li>
 *   <li>{@code userId} — database primary key</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {

    private final JwtConfig jwtConfig;

    // ────────────────────────────────────────────────────────────────
    //  Token Generation
    // ────────────────────────────────────────────────────────────────

    /**
     * Generates a signed JWT from a Spring Security {@link Authentication}.
     *
     * @param authentication the authenticated principal
     * @param userId         the database ID of the user
     * @return signed JWT string
     */
    public String generateToken(Authentication authentication, Long userId) {
        String email = authentication.getName();
        String roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        Date now    = new Date();
        Date expiry = new Date(now.getTime() + jwtConfig.getExpirationMs());

        return Jwts.builder()
                .subject(email)
                .claim("roles", roles)
                .claim("userId", userId)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Generates a token directly from email + role string (used in AuthService).
     */
    public String generateToken(String email, String roles, Long userId) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + jwtConfig.getExpirationMs());

        return Jwts.builder()
                .subject(email)
                .claim("roles", roles)
                .claim("userId", userId)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    // ────────────────────────────────────────────────────────────────
    //  Token Parsing
    // ────────────────────────────────────────────────────────────────

    /** Extracts the email (subject) from a valid JWT */
    public String getEmailFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    /** Extracts the roles string from a valid JWT */
    public String getRolesFromToken(String token) {
        return parseClaims(token).get("roles", String.class);
    }

    /** Extracts the userId from a valid JWT */
    public Long getUserIdFromToken(String token) {
        return parseClaims(token).get("userId", Long.class);
    }

    /** Returns the token expiry time in epoch milliseconds */
    public long getExpirationEpoch(String token) {
        return parseClaims(token).getExpiration().getTime();
    }

    // ────────────────────────────────────────────────────────────────
    //  Token Validation
    // ────────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the token is structurally valid, properly
     * signed, and not expired.
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("Malformed JWT: {}", e.getMessage());
        } catch (SecurityException e) {
            log.warn("Invalid JWT signature: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Empty JWT claims: {}", e.getMessage());
        }
        return false;
    }

    // ────────────────────────────────────────────────────────────────
    //  Internal helpers
    // ────────────────────────────────────────────────────────────────

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtConfig.getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
