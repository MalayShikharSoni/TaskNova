package com.tasknova.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for POST /api/auth/login.
 * The {@code email} field accepts either an email address or a username.
 */
@Data
public class LoginRequest {

    @NotBlank(message = "Email or username is required")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}
