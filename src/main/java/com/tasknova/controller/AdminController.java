package com.tasknova.controller;

import com.tasknova.dto.admin.AdminDashboardDto;
import com.tasknova.dto.admin.AuditLogDto;
import com.tasknova.dto.task.TaskResponseDto;
import com.tasknova.dto.user.UserResponseDto;
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
        model.addAttribute("search",      search);
        model.addAttribute("currentPage", page);
        return "admin/users";
    }

    /** Single user detail page (admin view) */
    @GetMapping("/users/{id}")
    public String userDetail(@PathVariable Long id, Model model) {
        UserResponseDto user = adminService.getUserById(id);
        model.addAttribute("user", user);
        return "admin/user-detail";
    }

    /** System-wide task management page */
    @GetMapping("/tasks")
    public String taskManagement(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            Model model
    ) {
        Page<TaskResponseDto> tasks = adminService.getAllTasks(search, page);
        model.addAttribute("tasks",       tasks);
        model.addAttribute("search",      search);
        model.addAttribute("currentPage", page);
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
        model.addAttribute("actor",       actor);
        model.addAttribute("currentPage", page);
        return "admin/audit-logs";
    }
}
