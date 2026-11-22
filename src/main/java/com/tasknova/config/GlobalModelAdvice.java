package com.tasknova.config;

import com.tasknova.controller.AdminController;
import com.tasknova.controller.DashboardController;
import com.tasknova.controller.ProfileController;
import com.tasknova.controller.TaskController;
import com.tasknova.entity.enums.Role;
import com.tasknova.entity.enums.TaskStatus;
import com.tasknova.repository.TaskRepository;
import com.tasknova.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.LocalDate;

/**
 * Model attributes shared by every page that renders the app shell.
 *
 * <p>Scoped to the page controllers so REST calls don't pay for the extra lookup.
 */
@ControllerAdvice(assignableTypes = {
        DashboardController.class, TaskController.class,
        AdminController.class, ProfileController.class
})
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;

    /** Signed-in user's display info for the sidebar, plus their open/overdue counters. */
    public record CurrentUser(Long id, String username, String email, String initial, boolean admin,
                              long openTasks, long overdueTasks) {}

    /** Current request URI, used to highlight the active nav item. */
    @ModelAttribute("currentUri")
    public String currentUri(HttpServletRequest request) {
        return request != null ? request.getRequestURI() : "";
    }

    @ModelAttribute("currentUser")
    public CurrentUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return userRepository.findByEmail(auth.getName())
                .map(u -> new CurrentUser(
                        u.getId(),
                        u.getUsername(),
                        u.getEmail(),
                        u.getUsername().substring(0, 1).toUpperCase(),
                        u.getRole() == Role.ADMIN,
                        taskRepository.countVisible(u.getId())
                                - taskRepository.countVisibleByStatus(u.getId(), TaskStatus.DONE),
                        taskRepository.countOverdueVisible(u.getId(), LocalDate.now())))
                .orElse(null);
    }
}
