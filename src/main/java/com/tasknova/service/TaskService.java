package com.tasknova.service;

import com.tasknova.dto.task.TaskCreateDto;
import com.tasknova.dto.task.TaskResponseDto;
import com.tasknova.dto.task.TaskUpdateDto;
import com.tasknova.entity.Tag;
import com.tasknova.entity.Task;
import com.tasknova.entity.User;
import com.tasknova.entity.enums.Priority;
import com.tasknova.entity.enums.TaskStatus;
import com.tasknova.exception.ResourceNotFoundException;
import com.tasknova.exception.UnauthorizedException;
import com.tasknova.repository.TagRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Business logic for task CRUD operations.
 * Enforces ownership rules — users can only manage their own tasks
 * unless they are admins.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TagRepository  tagRepository;
    private final AuditService   auditService;

    private static final int DEFAULT_PAGE_SIZE = 10;

    // ────────────────────────────────────────────────────────────────
    //  Create
    // ────────────────────────────────────────────────────────────────

    @Transactional
    public TaskResponseDto createTask(TaskCreateDto dto, Long ownerId) {
        User owner = findUserById(ownerId);

        User assignee = null;
        if (dto.getAssigneeId() != null) {
            assignee = findUserById(dto.getAssigneeId());
        }

        Set<Tag> tags = resolveOrCreateTags(dto.getTagNames());

        Task task = Task.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .dueDate(dto.getDueDate())
                .priority(dto.getPriority() != null ? dto.getPriority() : Priority.MEDIUM)
                .status(TaskStatus.TODO)
                .owner(owner)
                .assignee(assignee)
                .tags(tags)
                .build();

        Task saved = taskRepository.save(task);
        log.info("Task created: id={}, owner={}", saved.getId(), owner.getEmail());
        auditService.log(owner.getEmail(), "TASK_CREATED", "Task", saved.getId(),
                "Created task: " + saved.getTitle());

        return toDto(saved);
    }

    // ────────────────────────────────────────────────────────────────
    //  Read
    // ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public TaskResponseDto getTaskById(Long taskId, Long requestingUserId, boolean isAdmin) {
        Task task = findTaskById(taskId);
        assertAccess(task, requestingUserId, isAdmin);
        return toDto(task);
    }

    @Transactional(readOnly = true)
    public Page<TaskResponseDto> getTasksForUser(
            Long userId, TaskStatus status, Priority priority, int page) {
        Pageable pageable = PageRequest.of(page, DEFAULT_PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Task> tasks = taskRepository.findByOwnerFiltered(userId, status, priority, pageable);
        return tasks.map(this::toDto);
    }

    @Transactional(readOnly = true)
    public List<TaskResponseDto> getRecentTasksForUser(Long userId) {
        Pageable top5 = PageRequest.of(0, 5);
        return taskRepository.findTop5ByOwnerIdOrderByCreatedAtDesc(userId, top5)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    // ────────────────────────────────────────────────────────────────
    //  Update
    // ────────────────────────────────────────────────────────────────

    @Transactional
    public TaskResponseDto updateTask(Long taskId, TaskUpdateDto dto,
                                      Long requestingUserId, boolean isAdmin) {
        Task task = findTaskById(taskId);
        assertAccess(task, requestingUserId, isAdmin);

        if (dto.getTitle()       != null) task.setTitle(dto.getTitle());
        if (dto.getDescription() != null) task.setDescription(dto.getDescription());
        if (dto.getDueDate()     != null) task.setDueDate(dto.getDueDate());
        if (dto.getPriority()    != null) task.setPriority(dto.getPriority());
        if (dto.getStatus()      != null) task.setStatus(dto.getStatus());

        if (dto.getTagNames() != null) {
            task.getTags().clear();
            task.getTags().addAll(resolveOrCreateTags(dto.getTagNames()));
        }

        // Only admins can change the assignee
        if (isAdmin && dto.getAssigneeId() != null) {
            User assignee = findUserById(dto.getAssigneeId());
            task.setAssignee(assignee);
        }

        Task updated = taskRepository.save(task);
        log.info("Task updated: id={}", taskId);

        String actor = findUserById(requestingUserId).getEmail();
        auditService.log(actor, "TASK_UPDATED", "Task", taskId,
                "Updated task: " + updated.getTitle());

        return toDto(updated);
    }

    // ────────────────────────────────────────────────────────────────
    //  Delete
    // ────────────────────────────────────────────────────────────────

    @Transactional
    public void deleteTask(Long taskId, Long requestingUserId, boolean isAdmin) {
        Task task = findTaskById(taskId);
        assertAccess(task, requestingUserId, isAdmin);

        String actor = findUserById(requestingUserId).getEmail();
        String title = task.getTitle();

        taskRepository.delete(task);
        log.info("Task deleted: id={}", taskId);
        auditService.log(actor, "TASK_DELETED", "Task", taskId, "Deleted task: " + title);
    }

    // ────────────────────────────────────────────────────────────────
    //  Dashboard stats
    // ────────────────────────────────────────────────────────────────

    public long countByOwnerAndStatus(Long ownerId, TaskStatus status) {
        return taskRepository.countByOwnerIdAndStatus(ownerId, status);
    }

    public long countOverdueByOwner(Long ownerId) {
        return taskRepository.countOverdueByOwner(ownerId, LocalDate.now());
    }

    public long countAllByOwner(Long ownerId) {
        return taskRepository.countByOwnerId(ownerId);
    }

    // ────────────────────────────────────────────────────────────────
    //  Internal helpers
    // ────────────────────────────────────────────────────────────────

    private Task findTaskById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task", "id", id));
    }

    private User findUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    /**
     * Enforces ownership: non-admins can only access tasks they own or are assigned to.
     */
    private void assertAccess(Task task, Long requestingUserId, boolean isAdmin) {
        if (isAdmin) return;
        boolean isOwner    = task.getOwner().getId().equals(requestingUserId);
        boolean isAssignee = task.getAssignee() != null
                && task.getAssignee().getId().equals(requestingUserId);
        if (!isOwner && !isAssignee) {
            throw new UnauthorizedException("You do not have permission to access this task.");
        }
    }

    /**
     * Finds existing tags by name or creates new ones on the fly.
     */
    private Set<Tag> resolveOrCreateTags(Set<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) return new HashSet<>();

        Set<Tag> result = new HashSet<>();
        for (String name : tagNames) {
            String trimmed = name.trim().toLowerCase();
            if (trimmed.isEmpty()) continue;
            Tag tag = tagRepository.findByNameIgnoreCase(trimmed)
                    .orElseGet(() -> tagRepository.save(
                            Tag.builder().name(trimmed).build()));
            result.add(tag);
        }
        return result;
    }

    // ────────────────────────────────────────────────────────────────
    //  Entity → DTO mapping
    // ────────────────────────────────────────────────────────────────

    public TaskResponseDto toDto(Task task) {
        return TaskResponseDto.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .dueDate(task.getDueDate())
                .priority(task.getPriority())
                .status(task.getStatus())
                .overdue(task.isOverdue())
                .ownerId(task.getOwner().getId())
                .ownerUsername(task.getOwner().getUsername())
                .assigneeId(task.getAssignee() != null ? task.getAssignee().getId() : null)
                .assigneeUsername(task.getAssignee() != null ? task.getAssignee().getUsername() : null)
                .tags(task.getTags().stream().map(Tag::getName).collect(Collectors.toSet()))
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
