package com.tasknova.controller;

import com.tasknova.dto.task.TaskResponseDto;
import com.tasknova.dto.user.UserResponseDto;
import com.tasknova.entity.enums.Priority;
import com.tasknova.entity.enums.TaskStatus;
import com.tasknova.repository.TagRepository;
import com.tasknova.service.TaskService;
import com.tasknova.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * Serves Thymeleaf pages for the user task management portal.
 * Task mutations (create/update/delete) are done via the REST API from JS.
 */
@Controller
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final UserService userService;
    private final TagRepository tagRepository;

    /** Main task list page with filtering and pagination */
    @GetMapping
    public String taskList(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) Priority priority,
            @RequestParam(defaultValue = "0") int page,
            Model model
    ) {
        UserResponseDto user = userService.getUserByEmail(userDetails.getUsername());
        Page<TaskResponseDto> tasks = taskService.getTasksForUser(
                user.getId(), status, priority, page);

        model.addAttribute("user",       user);
        model.addAttribute("tasks",      tasks);
        model.addAttribute("statuses",   TaskStatus.values());
        model.addAttribute("priorities", Priority.values());
        model.addAttribute("selectedStatus",   status);
        model.addAttribute("selectedPriority", priority);
        model.addAttribute("allTags",    tagRepository.findAll());
        model.addAttribute("currentPage", page);

        return "user/tasks";
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
        model.addAttribute("statuses",   TaskStatus.values());
        model.addAttribute("priorities", Priority.values());
        model.addAttribute("allTags",    tagRepository.findAll());

        return "user/task-detail";
    }
}
