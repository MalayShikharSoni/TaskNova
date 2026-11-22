package com.tasknova.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Sends the bare root URL somewhere useful: admins to the admin console,
 * everyone else to their dashboard. Anonymous visitors are redirected to
 * the login page by Spring Security before reaching this.
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Authentication authentication) {
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return isAdmin ? "redirect:/admin" : "redirect:/dashboard";
    }
}
