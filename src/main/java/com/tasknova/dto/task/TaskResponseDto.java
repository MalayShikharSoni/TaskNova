package com.tasknova.dto.task;

import com.tasknova.entity.enums.Priority;
import com.tasknova.entity.enums.TaskStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Response DTO for task data returned to the client.
 * Contains all fields needed by Thymeleaf templates and REST consumers.
 */
@Data
@Builder
public class TaskResponseDto {

    private Long        id;
    private String      title;
    private String      description;
    private LocalDate   dueDate;
    private Priority    priority;
    private TaskStatus  status;

    /** Human-readable status label e.g. "In Progress" */
    private String      statusLabel;

    private String      ownerUsername;
    private String      assigneeUsername;

    /** Tag names (not entities) for easy template rendering */
    private Set<String> tags;

    /** True if past due date and not yet Done */
    private boolean     overdue;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
