package com.tasknova.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Serves the login and registration HTML pages.
 * Actual auth logic is handled by {@code AuthApiController} via REST.
 */
@Controller
@RequestMapping("/auth")
public class AuthController {

    /** Login page — also handles ?error and ?logout query params for flash messages */
    @GetMapping("/login")
    public String loginPage() {
        return "auth/login";
    }

    /** Registration page */
    @GetMapping("/register")
    public String registerPage() {
        return "auth/register";
    }
}
