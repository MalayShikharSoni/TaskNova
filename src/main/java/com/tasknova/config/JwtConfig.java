package com.tasknova.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * JWT configuration properties bound from application.properties.
 * Centralises all JWT-related settings in one place.
 */
@Configuration
@Data
public class JwtConfig {

    /** HMAC-SHA256 signing secret — must be at least 256 bits (32 chars) */
    @Value("${app.jwt.secret:${jwt.secret:3cfa76ef14937c1c0ea519f8fc057a80fcd04a7e7d0d9b2a69ec87f8db41e39c747d8a1234567890abcdef}}")
    private String secret;

    /** Token expiry in milliseconds (default: 24h = 86_400_000 ms) */
    @Value("${app.jwt.expiration-ms:${jwt.expiration-ms:86400000}}")
    private long expirationMs;

    /** Token type prefix used in Authorization header */
    public static final String TOKEN_PREFIX = "Bearer ";

    /** Authorization header name */
    public static final String HEADER_NAME = "Authorization";
}
