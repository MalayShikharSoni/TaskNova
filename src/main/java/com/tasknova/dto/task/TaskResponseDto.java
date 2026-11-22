package com.tasknova.dto.task;

import com.tasknova.dto.tag.TagDto;
import com.tasknova.entity.enums.Priority;
import com.tasknova.entity.enums.TaskStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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

    private Long        ownerId;
    private String      ownerUsername;
    private Long        assigneeId;
    private String      assigneeUsername;

    /** Tag names, sorted, for simple clients */
    private Set<String> tags;

    /** Tag names with their badge colours, sorted by name */
    private List<TagDto> tagDetails;

    /** True if past due date and not yet Done */
    private boolean     overdue;

    /** Days from today until the due date (negative = past), null when there's no due date */
    private Long        dueInDays;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
