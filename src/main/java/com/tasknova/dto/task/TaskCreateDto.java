package com.tasknova.dto.task;

import com.tasknova.entity.enums.Priority;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * DTO for creating a new task via POST /api/tasks
 */
@Data
public class TaskCreateDto {

    @NotBlank(message = "Task title is required")
    @Size(max = 200, message = "Title cannot exceed 200 characters")
    private String title;

    @Size(max = 5000, message = "Description cannot exceed 5000 characters")
    private String description;

    @FutureOrPresent(message = "Due date must be today or in the future")
    private LocalDate dueDate;

    private Priority priority = Priority.MEDIUM;

    /** Tag names to attach (will be created if they don't exist) */
    private Set<String> tagNames = new HashSet<>();

    /** Only admins can populate this field */
    private Long assigneeId;
}
