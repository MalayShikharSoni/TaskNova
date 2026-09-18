package com.tasknova.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Exposes global model attributes to all Thymeleaf views.
 */
@ControllerAdvice
public class GlobalModelAdvice {

    /**
     * Exposes the current request URI as 'currentUri' to avoid deprecated
     * #httpServletRequest expression issues in Thymeleaf / Spring 6.
     */
    @ModelAttribute("currentUri")
    public String currentUri(HttpServletRequest request) {
        return request != null ? request.getRequestURI() : "";
    }
}
