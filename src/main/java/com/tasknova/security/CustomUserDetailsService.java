package com.tasknova.security;

import com.tasknova.entity.User;
import com.tasknova.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Loads user-specific data for Spring Security authentication.
 *
 * <p>Used by:
 * <ul>
 *   <li>Form login (Thymeleaf pages) — Spring Security calls this via
 *       {@link org.springframework.security.authentication.dao.DaoAuthenticationProvider}</li>
 *   <li>{@code SecurityConfig} for the {@code AuthenticationProvider} bean</li>
 * </ul>
 *
 * <p>Login is performed using the user's <strong>email</strong> address,
 * not their username.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Loads a {@link UserDetails} by email.
     *
     * @param email the login identifier
     * @throws UsernameNotFoundException if no active user with this email exists
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Authentication failed — user not found for email: {}", email);
                    return new UsernameNotFoundException(
                            "No account found with email: " + email
                    );
                });

        if (!user.isActive()) {
            log.warn("Authentication blocked — account is deactivated for email: {}", email);
            throw new UsernameNotFoundException("Account is deactivated: " + email);
        }

        // Spring Security requires "ROLE_" prefix for hasRole() checks
        String grantedRole = "ROLE_" + user.getRole().name();

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())           // principal name = email
                .password(user.getPassword())        // BCrypt hash
                .authorities(List.of(new SimpleGrantedAuthority(grantedRole)))
                .accountExpired(false)
                .accountLocked(!user.isActive())
                .credentialsExpired(false)
                .disabled(!user.isActive())
                .build();
    }
}
