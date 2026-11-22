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
import java.util.List;

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
        long totalTasks = taskRepository.count();
        long doneTasks  = taskRepository.countByStatus(TaskStatus.DONE);
        List<AuditLogDto> recent = auditLogRepository
                .findAllByOrderByTimestampDesc(PageRequest.of(0, 8))
                .map(this::toAuditDto)
                .getContent();

        return AdminDashboardDto.builder()
                .totalUsers(userRepository.count())
                .activeUsers(userRepository.countByActiveTrue())
                .totalAdmins(userRepository.countByRole(Role.ADMIN))
                .totalTasks(totalTasks)
                .todoTasks(taskRepository.countByStatus(TaskStatus.TODO))
                .inProgressTasks(taskRepository.countByStatus(TaskStatus.IN_PROGRESS))
                .doneTasks(doneTasks)
                .overdueTasks(taskRepository.countOverdueAll(LocalDate.now()))
                .completionPercent(totalTasks == 0 ? 0 : (int) Math.round(doneTasks * 100.0 / totalTasks))
                .recentActivity(recent)
                .build();
    }

    // ────────────────────────────────────────────────────────────────
    //  User Management
    // ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<UserResponseDto> getAllUsers(String search, int page) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE,
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

        if (user.getEmail().equalsIgnoreCase(adminEmail)) {
            throw new ValidationException("You cannot deactivate your own account.");
        }
        if (user.getRole() == Role.ADMIN) {
            throw new ValidationException("Admins can't be deactivated. Change their role to USER first.");
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
        if (newRole == null) {
            throw new ValidationException("A role is required.");
        }
        User user = findUserById(userId);
        if (user.getEmail().equalsIgnoreCase(adminEmail)) {
            // Guarding self-changes also guarantees at least one admin always remains.
            throw new ValidationException("You cannot change your own role.");
        }
        Role oldRole = user.getRole();
        if (oldRole == newRole) {
            return userService.toDto(user);
        }
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
        return getAllTasks(search, null, page);
    }

    @Transactional(readOnly = true)
    public Page<TaskResponseDto> getAllTasks(String search, TaskStatus status, int page) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        String q = search == null ? "" : search.trim();
        return taskRepository.searchTasks(q, status, pageable).map(taskService::toDto);
    }

    /** Tasks owned by one user, newest first (admin user-detail page). */
    @Transactional(readOnly = true)
    public Page<TaskResponseDto> getTasksOwnedBy(Long userId, int page) {
        findUserById(userId);
        Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return taskRepository.findByOwnerId(userId, pageable).map(taskService::toDto);
    }

    /** Active users for the "assign to" picker. */
    @Transactional(readOnly = true)
    public List<UserResponseDto> getAssignableUsers() {
        return userRepository.findAllByActiveTrueOrderByUsernameAsc().stream()
                .map(u -> UserResponseDto.builder()
                        .id(u.getId()).username(u.getUsername()).email(u.getEmail())
                        .role(u.getRole()).active(u.isActive()).createdAt(u.getCreatedAt())
                        .build())
                .toList();
    }

    // ────────────────────────────────────────────────────────────────
    //  Audit Logs
    // ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AuditLogDto> getAuditLogs(int page) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE);
        return auditLogRepository.findAllByOrderByTimestampDesc(pageable)
                .map(this::toAuditDto);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDto> getAuditLogsByActor(String actorUsername, int page) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "timestamp"));
        return auditLogRepository.findAllByActorUsernameContainingIgnoreCase(actorUsername.trim(), pageable)
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
