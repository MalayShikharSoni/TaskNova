package com.tasknova.service;

import com.tasknova.dto.user.UserResponseDto;
import com.tasknova.dto.user.UserUpdateDto;
import com.tasknova.entity.User;
import com.tasknova.exception.ResourceNotFoundException;
import com.tasknova.exception.UnauthorizedException;
import com.tasknova.exception.ValidationException;
import com.tasknova.repository.TaskRepository;
import com.tasknova.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for user self-service operations.
 * Admin-level user management is handled by {@link AdminService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository  userRepository;
    private final TaskRepository  taskRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService    auditService;

    // ────────────────────────────────────────────────────────────────
    //  Read
    // ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public UserResponseDto getUserById(Long id) {
        User user = findById(id);
        return toDto(user);
    }

    @Transactional(readOnly = true)
    public UserResponseDto getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        return toDto(user);
    }

    // ────────────────────────────────────────────────────────────────
    //  Update own profile
    // ────────────────────────────────────────────────────────────────

    @Transactional
    public UserResponseDto updateProfile(Long userId, UserUpdateDto dto) {
        User user = findById(userId);

        // Username change
        if (dto.getUsername() != null && !dto.getUsername().equals(user.getUsername())) {
            if (userRepository.existsByUsername(dto.getUsername())) {
                throw new ValidationException("Username '" + dto.getUsername() + "' is already taken.");
            }
            user.setUsername(dto.getUsername());
        }

        // Password change — requires current password verification
        if (dto.getNewPassword() != null) {
            if (dto.getCurrentPassword() == null) {
                throw new ValidationException("Current password is required to set a new password.");
            }
            if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
                throw new UnauthorizedException("Current password is incorrect.");
            }
            user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
            log.info("Password changed for user: {}", user.getEmail());
        }

        User saved = userRepository.save(user);
        auditService.log(user.getEmail(), "PROFILE_UPDATED", "User", userId,
                "User updated their profile");
        return toDto(saved);
    }

    // ────────────────────────────────────────────────────────────────
    //  Helpers
    // ────────────────────────────────────────────────────────────────

    private User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    public UserResponseDto toDto(User user) {
        long totalTasks = taskRepository.countByOwnerId(user.getId());
        return UserResponseDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .totalTasks(totalTasks)
                .build();
    }
}
