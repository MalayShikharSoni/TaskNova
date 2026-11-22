package com.tasknova.security;

import com.tasknova.config.JwtConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Issues the browser session cookie that carries the JWT.
 *
 * <p>The cookie is {@code HttpOnly} (page scripts never see the token),
 * {@code SameSite=Lax} (not sent on cross-site POSTs) and {@code Secure}
 * whenever the request arrived over HTTPS — including behind a TLS-terminating
 * proxy, thanks to {@code server.forward-headers-strategy=framework}.
 */
@Component
@RequiredArgsConstructor
public class JwtCookieService {

    public static final String COOKIE_NAME = "tasknova_jwt";

    private final JwtConfig jwtConfig;

    public void write(String token, HttpServletRequest request, HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, token)
                .path("/")
                .maxAge(Duration.ofMillis(jwtConfig.getExpirationMs()))
                .sameSite("Lax")
                .httpOnly(true)
                .secure(request != null && request.isSecure())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
