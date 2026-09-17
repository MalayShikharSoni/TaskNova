package com.tasknova.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Read-only view of an AuditLog entry for the admin portal.
 */
@Data
@Builder
public class AuditLogDto {

    private Long id;
    private String actorUsername;
    private String action;
    private String targetType;
    private Long targetId;
    private LocalDateTime timestamp;
    private String details;
}
