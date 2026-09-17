package com.tasknova.controller;

import com.tasknova.dto.task.TaskResponseDto;
import com.tasknova.dto.user.UserResponseDto;
import com.tasknova.entity.enums.TaskStatus;
import com.tasknova.service.TaskService;
import com.tasknova.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * Serves the authenticated user's dashboard page.
 * Populates the model with task statistics and recent tasks.
 */
@Controller
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final TaskService taskService;
    private final UserService userService;

    @GetMapping
    public String dashboard(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        UserResponseDto user = userService.getUserByEmail(userDetails.getUsername());

        // Stats
        long totalTasks    = taskService.countAllByOwner(user.getId());
        long todoCount     = taskService.countByOwnerAndStatus(user.getId(), TaskStatus.TODO);
        long inProgCount   = taskService.countByOwnerAndStatus(user.getId(), TaskStatus.IN_PROGRESS);
        long doneCount     = taskService.countByOwnerAndStatus(user.getId(), TaskStatus.DONE);
        long overdueCount  = taskService.countOverdueByOwner(user.getId());

        // Recent tasks widget
        List<TaskResponseDto> recentTasks = taskService.getRecentTasksForUser(user.getId());

        model.addAttribute("user",         user);
        model.addAttribute("totalTasks",   totalTasks);
        model.addAttribute("todoCount",    todoCount);
        model.addAttribute("inProgCount",  inProgCount);
        model.addAttribute("doneCount",    doneCount);
        model.addAttribute("overdueCount", overdueCount);
        model.addAttribute("recentTasks",  recentTasks);

        return "user/dashboard";
    }
}
