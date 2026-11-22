package com.tasknova.config;

import com.tasknova.entity.User;
import com.tasknova.entity.enums.Role;
import com.tasknova.repository.UserRepository;
import com.tasknova.security.CustomUserDetailsService;
import com.tasknova.security.JwtAuthenticationFilter;
import com.tasknova.security.JwtCookieService;
import com.tasknova.security.JwtTokenProvider;
import com.tasknova.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
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
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.LinkedHashMap;

/**
 * Central Spring Security configuration.
 *
 * <p>Dual-layer security:
 * <ul>
 *   <li>Form login for the Thymeleaf UI — on success an HttpOnly JWT cookie is issued,
 *       and every page request is authenticated from that cookie.</li>
 *   <li>{@code Authorization: Bearer} JWT for {@code /api/**} clients.
 *       Unauthenticated API calls get a plain {@code 401}, never a login-page redirect.</li>
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
    private final JwtCookieService jwtCookieService;
    private final UserRepository userRepository;
    private final AuditService auditService;

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
            // CSRF tokens protect the HTML forms (logout). The JSON API is protected by the
            // SameSite=Lax cookie + JSON content type, and Bearer clients aren't cookie-based.
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**", "/h2-console/**", "/auth/login-process")
            )

            // Allow H2 console frames
            .headers(headers -> headers
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
            )

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_RESOURCES).permitAll()
                .requestMatchers(PUBLIC_PAGES).permitAll()
                .requestMatchers(PUBLIC_API).permitAll()

                // Admin portal — pages + API
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // Actuator sensitive endpoints — admin only
                .requestMatchers("/actuator/**").hasRole("ADMIN")

                .anyRequest().authenticated()
            )

            // API clients get a plain 401; everything else is sent to the login page
            .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint()))

            // Form login for Thymeleaf pages
            .formLogin(form -> form
                .loginPage("/auth/login")
                .loginProcessingUrl("/auth/login-process")
                .successHandler((request, response, authentication) -> {
                    UserDetails userDetails = (UserDetails) authentication.getPrincipal();
                    User user = userRepository.findByEmailOrUsername(userDetails.getUsername()).orElse(null);
                    if (user == null) {
                        response.sendRedirect("/dashboard");
                        return;
                    }
                    String token = jwtTokenProvider.generateToken(
                            user.getEmail(), "ROLE_" + user.getRole().name(), user.getId());
                    jwtCookieService.write(token, request, response);
                    auditService.log(user.getEmail(), "USER_LOGIN", "User", user.getId(), "Login successful (web)");
                    response.sendRedirect(user.getRole() == Role.ADMIN ? "/admin" : "/dashboard");
                })
                .failureUrl("/auth/login?error=true")
                .usernameParameter("email")
                .passwordParameter("password")
                .permitAll()
            )

            // Logout — POST only, so a stray <img src="/auth/logout"> can't sign people out
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/auth/logout", "POST"))
                .logoutSuccessUrl("/auth/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID", JwtCookieService.COOKIE_NAME)
                .permitAll()
            )

            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )

            // JWT filter runs before the username/password filter (Bearer header or cookie)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

            .authenticationProvider(authenticationProvider());

        return http.build();
    }

    // ────────────────────────────────────────────────────────────────
    //  Beans
    // ────────────────────────────────────────────────────────────────

    private AuthenticationEntryPoint authenticationEntryPoint() {
        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> entryPoints = new LinkedHashMap<>();
        entryPoints.put(new AntPathRequestMatcher("/api/**"), new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));
        DelegatingAuthenticationEntryPoint delegating = new DelegatingAuthenticationEntryPoint(entryPoints);
        delegating.setDefaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/auth/login"));
        return delegating;
    }

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
