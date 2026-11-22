package com.tasknova.controller;

import com.tasknova.seed.DataSeeder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Serves the login and registration HTML pages.
 * Credentials are posted to Spring Security (/auth/login-process)
 * or to /api/auth/* from JavaScript.
 */
@Controller
@RequestMapping("/auth")
public class AuthController {

    @Value("${app.seed.show-demo-hint:true}")
    private boolean showDemoHint;

    @Value("${app.seed.demo-password:" + DataSeeder.DEFAULT_DEMO_PASSWORD + "}")
    private String demoPassword;

    @Value("${app.seed.admin-password:" + DataSeeder.DEFAULT_ADMIN_PASSWORD + "}")
    private String adminPassword;

    /** Login page. Also handles the error, logout, registered and expired flash params. */
    @GetMapping("/login")
    public String loginPage(Authentication authentication, Model model) {
        if (isSignedIn(authentication)) {
            return "redirect:/";
        }
        model.addAttribute("showDemoHint", showDemoHint);
        model.addAttribute("demoPassword", demoPassword);
        // Never advertise an admin password that was deliberately changed.
        model.addAttribute("showAdminHint",
                showDemoHint && DataSeeder.DEFAULT_ADMIN_PASSWORD.equals(adminPassword));
        return "auth/login";
    }

    /** Registration page */
    @GetMapping("/register")
    public String registerPage(Authentication authentication) {
        return isSignedIn(authentication) ? "redirect:/" : "auth/register";
    }

    private boolean isSignedIn(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
