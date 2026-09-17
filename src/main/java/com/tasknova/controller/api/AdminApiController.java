package com.tasknova.controller.api;

import com.tasknova.dto.admin.AdminDashboardDto;
import com.tasknova.dto.admin.AuditLogDto;
import com.tasknova.dto.task.TaskResponseDto;
import com.tasknova.dto.user.UserResponseDto;
import com.tasknova.entity.enums.Role;
import com.tasknova.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API for admin operations.
 * All endpoints require ROLE_ADMIN.
 *
 * <p>GET    /api/admin/dashboard        — system stats
 * <p>GET    /api/admin/users            — paginated user list
 * <p>GET    /api/admin/users/{id}       — single user
 * <p>PATCH  /api/admin/users/{id}/toggle-active — activate/deactivate
 * <p>PATCH  /api/admin/users/{id}/role  — change role
 * <p>GET    /api/admin/tasks            — all tasks (system-wide)
 * <p>GET    /api/admin/audit-logs       — audit trail
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminApiController {

    private final AdminService adminService;

    // ────────────────────────────────────────────────────────────────
    //  Dashboard
    // ────────────────────────────────────────────────────────────────

    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardDto> getDashboard() {
        return ResponseEntity.ok(adminService.getDashboardStats());
    }

    // ────────────────────────────────────────────────────────────────
    //  User Management
    // ────────────────────────────────────────────────────────────────

    @GetMapping("/users")
    public ResponseEntity<Page<UserResponseDto>> getUsers(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page
    ) {
        return ResponseEntity.ok(adminService.getAllUsers(search, page));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserResponseDto> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getUserById(id));
    }

    @PatchMapping("/users/{id}/toggle-active")
    public ResponseEntity<UserResponseDto> toggleActive(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UserResponseDto updated = adminService.toggleUserActive(id, userDetails.getUsername());
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/users/{id}/role")
    public ResponseEntity<UserResponseDto> changeRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Role newRole = Role.valueOf(body.get("role").toUpperCase());
        UserResponseDto updated = adminService.changeUserRole(id, newRole, userDetails.getUsername());
        return ResponseEntity.ok(updated);
    }

    // ────────────────────────────────────────────────────────────────
    //  Task Management (system-wide)
    // ────────────────────────────────────────────────────────────────

    @GetMapping("/tasks")
    public ResponseEntity<Page<TaskResponseDto>> getAllTasks(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page
    ) {
        return ResponseEntity.ok(adminService.getAllTasks(search, page));
    }

    // ────────────────────────────────────────────────────────────────
    //  Audit Logs
    // ────────────────────────────────────────────────────────────────

    @GetMapping("/audit-logs")
    public ResponseEntity<Page<AuditLogDto>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String actor
    ) {
        Page<AuditLogDto> logs = actor != null && !actor.isBlank()
                ? adminService.getAuditLogsByActor(actor, page)
                : adminService.getAuditLogs(page);
        return ResponseEntity.ok(logs);
    }
}
