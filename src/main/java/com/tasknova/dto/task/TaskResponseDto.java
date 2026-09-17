package com.tasknova.dto.task;

import com.tasknova.entity.enums.Priority;
import com.tasknova.entity.enums.TaskStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * DTO returned in API responses for a task.
 * Maps the Task entity to a clean, serializable shape.
 */
@Data
@Builder
public class TaskResponseDto {

    private Long id;
    private String title;
    private String description;
    private LocalDate dueDate;
    private Priority priority;
    private TaskStatus status;
    private boolean overdue;

    /** Owner info */
    private Long ownerId;
    private String ownerUsername;

    /** Assignee info (nullable) */
    private Long assigneeId;
    private String assigneeUsername;

    /** Tag names */
    private Set<String> tags;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Human-readable priority label */
    public String getPriorityLabel() {
        return priority != null ? priority.name() : "MEDIUM";
    }

    /** Human-readable status label */
    public String getStatusLabel() {
        if (status == null) return "TODO";
        return switch (status) {
            case TODO       -> "To Do";
            case IN_PROGRESS -> "In Progress";
            case DONE       -> "Done";
        };
    }
}
