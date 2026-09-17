package com.tasknova.dto.task;

import com.tasknova.entity.enums.Priority;
import com.tasknova.entity.enums.TaskStatus;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.Set;

/**
 * DTO for updating an existing task via PUT /api/tasks/{id}
 * All fields are optional — only non-null values will be applied.
 */
@Data
public class TaskUpdateDto {

    @Size(max = 200, message = "Title cannot exceed 200 characters")
    private String title;

    @Size(max = 5000, message = "Description cannot exceed 5000 characters")
    private String description;

    private LocalDate dueDate;

    private Priority priority;

    private TaskStatus status;

    /** Replaces the full tag set when provided */
    private Set<String> tagNames;

    /** Only admins can change the assignee */
    private Long assigneeId;
}
