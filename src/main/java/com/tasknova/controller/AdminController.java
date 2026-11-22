package com.tasknova.controller;

import com.tasknova.dto.admin.AdminDashboardDto;
import com.tasknova.dto.admin.AuditLogDto;
import com.tasknova.dto.task.TaskResponseDto;
import com.tasknova.dto.user.UserResponseDto;
import com.tasknova.entity.enums.TaskStatus;
import com.tasknova.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * Serves Thymeleaf pages for the Admin portal.
 * Access restricted to users with ROLE_ADMIN.
 */
@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    /** Admin dashboard — system-wide stats */
    @GetMapping
    public String adminDashboard(Model model) {
        AdminDashboardDto stats = adminService.getDashboardStats();
        model.addAttribute("stats", stats);
        return "admin/dashboard";
    }

    /** User management page */
    @GetMapping("/users")
    public String userManagement(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            Model model
    ) {
        Page<UserResponseDto> users = adminService.getAllUsers(search, page);
        model.addAttribute("users",       users);
        model.addAttribute("search",      search == null ? "" : search.trim());
        model.addAttribute("currentPage", Math.max(page, 0));
        return "admin/users";
    }

    /** Single user detail page (admin view): profile, task list, recent activity */
    @GetMapping("/users/{id}")
    public String userDetail(@PathVariable Long id,
                             @RequestParam(defaultValue = "0") int page,
                             Model model) {
        UserResponseDto member = adminService.getUserById(id);
        model.addAttribute("member",      member);
        model.addAttribute("tasks",       adminService.getTasksOwnedBy(id, page));
        model.addAttribute("activity",    adminService.getAuditLogsByActor(member.getEmail(), 0));
        model.addAttribute("currentPage", Math.max(page, 0));
        return "admin/user-detail";
    }

    /** System-wide task management page */
    @GetMapping("/tasks")
    public String taskManagement(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(defaultValue = "0") int page,
            Model model
    ) {
        Page<TaskResponseDto> tasks = adminService.getAllTasks(search, status, page);
        model.addAttribute("tasks",          tasks);
        model.addAttribute("search",         search == null ? "" : search.trim());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("statuses",       TaskStatus.values());
        model.addAttribute("currentPage",    Math.max(page, 0));
        return "admin/tasks";
    }

    /** Audit log viewer */
    @GetMapping("/audit-logs")
    public String auditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String actor,
            Model model
    ) {
        Page<AuditLogDto> logs = actor != null && !actor.isBlank()
                ? adminService.getAuditLogsByActor(actor, page)
                : adminService.getAuditLogs(page);

        model.addAttribute("logs",        logs);
        model.addAttribute("actor",       actor == null ? "" : actor.trim());
        model.addAttribute("currentPage", Math.max(page, 0));
        return "admin/audit-logs";
    }
}
