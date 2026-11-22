package com.tasknova.controller;

import com.tasknova.dto.task.TaskResponseDto;
import com.tasknova.dto.user.UserResponseDto;
import com.tasknova.entity.enums.Priority;
import com.tasknova.entity.enums.TaskStatus;
import com.tasknova.service.AdminService;
import com.tasknova.service.TaskService;
import com.tasknova.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Serves Thymeleaf pages for the user task workspace (list, board, detail).
 * Task mutations (create/update/delete) are done via the REST API from JS.
 */
@Controller
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService  taskService;
    private final UserService  userService;
    private final AdminService adminService;

    /** List view with search, filters and pagination */
    @GetMapping
    public String taskList(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "0") int page,
            Model model
    ) {
        UserResponseDto user = userService.getUserByEmail(userDetails.getUsername());
        Page<TaskResponseDto> tasks = taskService.getTasksForUser(user.getId(), status, priority, q, tag, page);

        boolean filtered = status != null || priority != null
                || (q != null && !q.isBlank()) || (tag != null && !tag.isBlank());

        model.addAttribute("user",        user);
        model.addAttribute("tasks",       tasks);
        model.addAttribute("statuses",    TaskStatus.values());
        model.addAttribute("priorities",  Priority.values());
        model.addAttribute("selectedStatus",   status);
        model.addAttribute("selectedPriority", priority);
        model.addAttribute("q",           q == null ? "" : q.trim());
        model.addAttribute("selectedTag", tag == null ? "" : tag.trim());
        model.addAttribute("filtered",    filtered);
        model.addAttribute("allTags",     taskService.getAllTags());
        model.addAttribute("currentPage", Math.max(page, 0));

        return "user/tasks";
    }

    /** Kanban board: drag cards between columns to change status */
    @GetMapping("/board")
    public String board(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        UserResponseDto user = userService.getUserByEmail(userDetails.getUsername());
        List<TaskResponseDto> all = taskService.getBoardTasks(user.getId());

        model.addAttribute("user",       user);
        model.addAttribute("todo",       all.stream().filter(t -> t.getStatus() == TaskStatus.TODO).toList());
        model.addAttribute("inProgress", all.stream().filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS).toList());
        model.addAttribute("done",       all.stream().filter(t -> t.getStatus() == TaskStatus.DONE).toList());
        model.addAttribute("allTags",    taskService.getAllTags());

        return "user/board";
    }

    /** Task detail / edit page */
    @GetMapping("/{id}")
    public String taskDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model
    ) {
        UserResponseDto user = userService.getUserByEmail(userDetails.getUsername());
        boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        TaskResponseDto task = taskService.getTaskById(id, user.getId(), isAdmin);

        model.addAttribute("user",       user);
        model.addAttribute("task",       task);
        model.addAttribute("isAdmin",    isAdmin);
        model.addAttribute("canDelete",  isAdmin || user.getId().equals(task.getOwnerId()));
        model.addAttribute("statuses",   TaskStatus.values());
        model.addAttribute("priorities", Priority.values());
        model.addAttribute("allTags",    taskService.getAllTags());
        if (isAdmin) {
            model.addAttribute("assignableUsers", adminService.getAssignableUsers());
        }

        return "user/task-detail";
    }
}
