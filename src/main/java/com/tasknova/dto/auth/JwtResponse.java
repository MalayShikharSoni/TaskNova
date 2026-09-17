package com.tasknova.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response body returned after a successful login.
 * The JWT token should be stored in localStorage by the client.
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
