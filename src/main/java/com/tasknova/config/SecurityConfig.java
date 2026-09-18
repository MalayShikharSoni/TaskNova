package com.tasknova.config;

import com.tasknova.entity.User;
import com.tasknova.entity.enums.Role;
import com.tasknova.repository.UserRepository;
import com.tasknova.security.CustomUserDetailsService;
import com.tasknova.security.JwtAuthenticationFilter;
import com.tasknova.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.time.Duration;

/**
 * Central Spring Security configuration.
 *
 * <p>Dual-layer security:
 * <ul>
 *   <li>Session-based (form login) for Thymeleaf page navigation</li>
 *   <li>JWT authentication for /api/** REST endpoints and cookie-based page auth</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtConfig jwtConfig;
    private final UserRepository userRepository;

    // ────────────────────────────────────────────────────────────────
    //  Publicly accessible paths
    // ────────────────────────────────────────────────────────────────
    private static final String[] PUBLIC_PAGES = {
        "/auth/login", "/auth/register",
        "/error", "/error/**"
    };

    private static final String[] PUBLIC_RESOURCES = {
        "/static/**", "/css/**", "/js/**", "/assets/**",
        "/favicon.ico", "/h2-console/**"
    };

    private static final String[] PUBLIC_API = {
        "/api/auth/**",
        "/actuator/health", "/actuator/info",
        "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };

    // ────────────────────────────────────────────────────────────────
    //  Main security filter chain
    // ────────────────────────────────────────────────────────────────
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for REST API calls and login process
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**", "/h2-console/**", "/auth/login-process")
            )

            // Allow H2 console frames
            .headers(headers -> headers
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
            )

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Static resources
                .requestMatchers(PUBLIC_RESOURCES).permitAll()
                // Public pages
                .requestMatchers(PUBLIC_PAGES).permitAll()
                // Public API
                .requestMatchers(PUBLIC_API).permitAll()

                // Admin portal — pages + API
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // Actuator sensitive endpoints — admin only
                .requestMatchers("/actuator/**").hasRole("ADMIN")

                // Everything else requires authentication
                .anyRequest().authenticated()
            )

            // Form login for Thymeleaf pages
            .formLogin(form -> form
                .loginPage("/auth/login")
                .loginProcessingUrl("/auth/login-process")
                .successHandler((request, response, authentication) -> {
                    UserDetails userDetails = (UserDetails) authentication.getPrincipal();
                    User user = userRepository.findByEmailOrUsername(userDetails.getUsername()).orElse(null);
                    if (user != null) {
                        String roleStr = "ROLE_" + user.getRole().name();
                        String token = jwtTokenProvider.generateToken(user.getEmail(), roleStr, user.getId());
                        ResponseCookie cookie = ResponseCookie.from("tasknova_jwt", token)
                                .path("/")
                                .maxAge(Duration.ofMillis(jwtConfig.getExpirationMs()))
                                .sameSite("Lax")
                                .httpOnly(false)
                                .build();
                        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

                        if (user.getRole() == Role.ADMIN) {
                            response.sendRedirect("/admin");
                        } else {
                            response.sendRedirect("/dashboard");
                        }
                    } else {
                        response.sendRedirect("/dashboard");
                    }
                })
                .failureUrl("/auth/login?error=true")
                .usernameParameter("email")
                .passwordParameter("password")
                .permitAll()
            )

            // Logout
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/auth/logout"))
                .logoutSuccessUrl("/auth/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID", "tasknova_jwt")
                .permitAll()
            )

            // Session management — stateful for pages, JWT filter adds auth per request
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )

            // JWT filter runs before username/password filter (guards /api/** and validates cookie)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

            // Use our custom UserDetailsService
            .authenticationProvider(authenticationProvider());

        return http.build();
    }

    // ────────────────────────────────────────────────────────────────
    //  Beans
    // ────────────────────────────────────────────────────────────────

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
