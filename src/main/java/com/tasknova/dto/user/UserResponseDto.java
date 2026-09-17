package com.tasknova.dto.user;

import com.tasknova.entity.enums.Role;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Safe, read-only view of a User — never exposes the password hash.
 */
@Data
@Builder
public class UserResponseDto {

    private Long id;
    private String username;
    private String email;
    private Role role;
    private boolean active;
    private LocalDateTime createdAt;

    /** Total tasks owned by this user */
    private long totalTasks;
}
