package com.tasknova.controller;

import com.tasknova.dto.user.UserResponseDto;
import com.tasknova.service.TaskService;
import com.tasknova.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Serves the authenticated user's dashboard page.
 * Counts include tasks the user owns and tasks assigned to them.
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

        model.addAttribute("user",    user);
        model.addAttribute("dash",    taskService.getDashboard(user.getId()));
        model.addAttribute("allTags", taskService.getAllTags());

        return "user/dashboard";
    }
}
