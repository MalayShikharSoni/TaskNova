package com.tasknova.dto.task;

import com.tasknova.entity.enums.Priority;
import com.tasknova.entity.enums.TaskStatus;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.Set;

/**
 * Body for {@code PUT} and {@code PATCH /api/tasks/{id}}.
 *
 * <ul>
 *   <li><strong>PATCH</strong> — partial: only non-null fields are applied.</li>
 *   <li><strong>PUT</strong> — replace: {@code title} is required; {@code description},
 *       {@code dueDate} and {@code tagNames} are set exactly as sent (null clears them);
 *       admins also replace {@code assigneeId} (null unassigns).
 *       {@code priority}/{@code status} keep their value when omitted.</li>
 * </ul>
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

    /** Names of existing tags; replaces the full tag set when applied */
    private Set<String> tagNames;

    /** Only admins can change the assignee */
    private Long assigneeId;
}
