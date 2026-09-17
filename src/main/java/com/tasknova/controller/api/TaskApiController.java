package com.tasknova.controller.api;

import com.tasknova.dto.task.TaskCreateDto;
import com.tasknova.dto.task.TaskResponseDto;
import com.tasknova.dto.task.TaskUpdateDto;
import com.tasknova.dto.user.UserResponseDto;
import com.tasknova.entity.enums.Priority;
import com.tasknova.entity.enums.TaskStatus;
import com.tasknova.exception.UnauthorizedException;
import com.tasknova.service.TaskService;
import com.tasknova.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for task CRUD operations.
 * All endpoints require a valid JWT Bearer token.
 *
 * <p>GET    /api/tasks         — paginated task list for current user
 * <p>POST   /api/tasks         — create new task
 * <p>GET    /api/tasks/{id}    — get single task
 * <p>PUT    /api/tasks/{id}    — full update
 * <p>PATCH  /api/tasks/{id}    — partial update (status change etc.)
 * <p>DELETE /api/tasks/{id}    — delete task
 * <p>GET    /api/tasks/recent  — 5 most recent tasks (dashboard widget)
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskApiController {

    private final TaskService taskService;
    private final UserService userService;

    // ────────────────────────────────────────────────────────────────
    //  GET all tasks for current user (paginated + filtered)
    // ────────────────────────────────────────────────────────────────
    @GetMapping
    public ResponseEntity<Page<TaskResponseDto>> getMyTasks(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) Priority priority,
            @RequestParam(defaultValue = "0") int page
    ) {
        Long userId = resolveUserId(userDetails);
        Page<TaskResponseDto> tasks = taskService.getTasksForUser(userId, status, priority, page);
        return ResponseEntity.ok(tasks);
    }

    // ────────────────────────────────────────────────────────────────
    //  GET recent tasks (dashboard widget)
    // ────────────────────────────────────────────────────────────────
    @GetMapping("/recent")
    public ResponseEntity<List<TaskResponseDto>> getRecentTasks(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = resolveUserId(userDetails);
        return ResponseEntity.ok(taskService.getRecentTasksForUser(userId));
    }

    // ────────────────────────────────────────────────────────────────
    //  GET single task
    // ────────────────────────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<TaskResponseDto> getTask(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId  = resolveUserId(userDetails);
        boolean isAdmin = isAdmin(userDetails);
        return ResponseEntity.ok(taskService.getTaskById(id, userId, isAdmin));
    }

    // ────────────────────────────────────────────────────────────────
    //  POST create task
    // ────────────────────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<TaskResponseDto> createTask(
            @Valid @RequestBody TaskCreateDto dto,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = resolveUserId(userDetails);

        // Non-admins cannot set assigneeId
        if (!isAdmin(userDetails) && dto.getAssigneeId() != null) {
            throw new UnauthorizedException("Only admins can assign tasks to other users.");
        }

        TaskResponseDto created = taskService.createTask(dto, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ────────────────────────────────────────────────────────────────
    //  PUT full update
    // ────────────────────────────────────────────────────────────────
    @PutMapping("/{id}")
    public ResponseEntity<TaskResponseDto> updateTask(
            @PathVariable Long id,
            @Valid @RequestBody TaskUpdateDto dto,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId  = resolveUserId(userDetails);
        boolean isAdmin = isAdmin(userDetails);

        if (!isAdmin && dto.getAssigneeId() != null) {
            throw new UnauthorizedException("Only admins can change task assignees.");
        }

        return ResponseEntity.ok(taskService.updateTask(id, dto, userId, isAdmin));
    }

    // ────────────────────────────────────────────────────────────────
    //  PATCH partial update (e.g. status-only change)
    // ────────────────────────────────────────────────────────────────
    @PatchMapping("/{id}")
    public ResponseEntity<TaskResponseDto> patchTask(
            @PathVariable Long id,
            @RequestBody TaskUpdateDto dto,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId  = resolveUserId(userDetails);
        boolean isAdmin = isAdmin(userDetails);
        return ResponseEntity.ok(taskService.updateTask(id, dto, userId, isAdmin));
    }

    // ────────────────────────────────────────────────────────────────
    //  DELETE task
    // ────────────────────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteTask(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId  = resolveUserId(userDetails);
        boolean isAdmin = isAdmin(userDetails);
        taskService.deleteTask(id, userId, isAdmin);
        return ResponseEntity.ok(Map.of("message", "Task deleted successfully."));
    }

    // ────────────────────────────────────────────────────────────────
    //  Helpers
    // ────────────────────────────────────────────────────────────────

    private Long resolveUserId(UserDetails userDetails) {
        UserResponseDto user = userService.getUserByEmail(userDetails.getUsername());
        return user.getId();
    }

    private boolean isAdmin(UserDetails userDetails) {
        return userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
