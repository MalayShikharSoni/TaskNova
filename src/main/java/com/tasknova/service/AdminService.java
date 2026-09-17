package com.tasknova.service;

import com.tasknova.dto.admin.AdminDashboardDto;
import com.tasknova.dto.admin.AuditLogDto;
import com.tasknova.dto.task.TaskResponseDto;
import com.tasknova.dto.user.UserResponseDto;
import com.tasknova.entity.AuditLog;
import com.tasknova.entity.User;
import com.tasknova.entity.enums.Role;
import com.tasknova.entity.enums.TaskStatus;
import com.tasknova.exception.ResourceNotFoundException;
import com.tasknova.exception.ValidationException;
import com.tasknova.repository.AuditLogRepository;
import com.tasknova.repository.TaskRepository;
import com.tasknova.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Admin-only business logic: user management, system-wide task view,
 * dashboard stats, and audit log retrieval.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserRepository    userRepository;
    private final TaskRepository    taskRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService      auditService;
    private final UserService       userService;
    private final TaskService       taskService;

    private static final int PAGE_SIZE = 10;

    // ────────────────────────────────────────────────────────────────
    //  Dashboard
    // ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AdminDashboardDto getDashboardStats() {
        return AdminDashboardDto.builder()
                .totalUsers(userRepository.count())
                .activeUsers(userRepository.countByActiveTrue())
                .totalAdmins(userRepository.countByRole(Role.ADMIN))
                .totalTasks(taskRepository.count())
                .todoTasks(taskRepository.countByStatus(TaskStatus.TODO))
                .inProgressTasks(taskRepository.countByStatus(TaskStatus.IN_PROGRESS))
                .doneTasks(taskRepository.countByStatus(TaskStatus.DONE))
                .overdueTasks(taskRepository.countOverdueAll(LocalDate.now()))
                .build();
    }

    // ────────────────────────────────────────────────────────────────
    //  User Management
    // ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<UserResponseDto> getAllUsers(String search, int page) {
        Pageable pageable = PageRequest.of(page, PAGE_SIZE,
                Sort.by(Sort.Direction.ASC, "username"));
        if (search != null && !search.isBlank()) {
            return userRepository.searchUsers(search.trim(), pageable)
                    .map(userService::toDto);
        }
        return userRepository.findAll(pageable).map(userService::toDto);
    }

    @Transactional(readOnly = true)
    public UserResponseDto getUserById(Long userId) {
        User user = findUserById(userId);
        return userService.toDto(user);
    }

    @Transactional
    public UserResponseDto toggleUserActive(Long userId, String adminEmail) {
        User user = findUserById(userId);

        if (user.getRole() == Role.ADMIN) {
            throw new ValidationException("Cannot deactivate another admin account.");
        }

        boolean wasActive = user.isActive();
        user.setActive(!wasActive);
        User saved = userRepository.save(user);

        String action = wasActive ? "USER_DEACTIVATED" : "USER_ACTIVATED";
        auditService.log(adminEmail, action, "User", userId,
                (wasActive ? "Deactivated" : "Activated") + " user: " + user.getEmail());
        log.info("User {} → active={}", user.getEmail(), saved.isActive());

        return userService.toDto(saved);
    }

    @Transactional
    public UserResponseDto changeUserRole(Long userId, Role newRole, String adminEmail) {
        User user = findUserById(userId);
        Role oldRole = user.getRole();
        user.setRole(newRole);
        User saved = userRepository.save(user);

        auditService.log(adminEmail, "USER_ROLE_CHANGED", "User", userId,
                "Role changed from " + oldRole + " to " + newRole + " for " + user.getEmail());
        log.info("Role changed for {}: {} → {}", user.getEmail(), oldRole, newRole);

        return userService.toDto(saved);
    }

    // ────────────────────────────────────────────────────────────────
    //  Task Management (system-wide)
    // ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<TaskResponseDto> getAllTasks(String search, int page) {
        Pageable pageable = PageRequest.of(page, PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        if (search != null && !search.isBlank()) {
            return taskRepository.searchTasks(search.trim(), pageable)
                    .map(taskService::toDto);
        }
        return taskRepository.findAll(pageable).map(taskService::toDto);
    }

    // ────────────────────────────────────────────────────────────────
    //  Audit Logs
    // ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AuditLogDto> getAuditLogs(int page) {
        Pageable pageable = PageRequest.of(page, PAGE_SIZE);
        return auditLogRepository.findAllByOrderByTimestampDesc(pageable)
                .map(this::toAuditDto);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDto> getAuditLogsByActor(String actorUsername, int page) {
        Pageable pageable = PageRequest.of(page, PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "timestamp"));
        return auditLogRepository.findAllByActorUsername(actorUsername, pageable)
                .map(this::toAuditDto);
    }

    // ────────────────────────────────────────────────────────────────
    //  Helpers
    // ────────────────────────────────────────────────────────────────

    private User findUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    private AuditLogDto toAuditDto(AuditLog log) {
        return AuditLogDto.builder()
                .id(log.getId())
                .actorUsername(log.getActorUsername())
                .action(log.getAction())
                .targetType(log.getTargetType())
                .targetId(log.getTargetId())
                .timestamp(log.getTimestamp())
                .details(log.getDetails())
                .build();
    }
}
