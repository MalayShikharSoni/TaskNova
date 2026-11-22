package com.tasknova.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response body returned after a successful login.
 *
 * <p>API clients send {@code token} as {@code Authorization: Bearer <token>}.
 * Browsers don't need to touch it: the same token is also set as an
 * HttpOnly {@code tasknova_jwt} cookie.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JwtResponse {

    private String token;

    @Builder.Default
    private String type = "Bearer";

    private Long userId;
    private String username;
    private String email;
    private String role;

    /** Expiry in milliseconds from epoch */
    private long expiresAt;
}
