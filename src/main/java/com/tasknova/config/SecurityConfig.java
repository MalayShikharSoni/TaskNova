package com.tasknova.config;

import com.tasknova.security.JwtAuthenticationFilter;
import com.tasknova.security.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Central Spring Security configuration.
 *
 * <p>Dual-layer security:
 * <ul>
 *   <li>Session-based (form login) for Thymeleaf page navigation</li>
 *   <li>JWT stateless for /api/** REST endpoints</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

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
            // Disable CSRF for REST API calls (JWT handles statefulness there)
            // Keep enabled for Thymeleaf form pages
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**", "/h2-console/**")
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
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/auth/login?error=true")
                .usernameParameter("email")
                .passwordParameter("password")
                .permitAll()
            )

            // Logout
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/auth/logout", "POST"))
                .logoutSuccessUrl("/auth/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )

            // Session management — stateful for pages, stateless for API (JWT filter handles it)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )

            // JWT filter runs before username/password filter (guards /api/**)
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
