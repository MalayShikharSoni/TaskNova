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

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TagRepository  tagRepository;
    private final AuditService   auditService;

    private static final int PAGE_SIZE = 10;

    // ────────────────────────────────────────────────────────────────
    //  Read
    // ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<TaskResponseDto> getTasksForUser(Long userId, TaskStatus status, Priority priority, int page) {
        Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
        return taskRepository.findByOwnerFiltered(userId, status, priority, pageable)
                             .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public TaskResponseDto getTaskById(Long taskId, Long requestingUserId, boolean isAdmin) {
        Task task = findById(taskId);
        if (!isAdmin && !task.getOwner().getId().equals(requestingUserId)) {
            throw new UnauthorizedException("You do not have access to this task.");
        }
        return toDto(task);
    }

    @Transactional(readOnly = true)
    public List<TaskResponseDto> getRecentTasksForUser(Long userId) {
        return taskRepository.findTop5ByOwnerIdOrderByCreatedAtDesc(userId)
                             .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long countAllByOwner(Long userId) {
        return taskRepository.countByOwnerId(userId);
    }

    @Transactional(readOnly = true)
    public long countByOwnerAndStatus(Long userId, TaskStatus status) {
        return taskRepository.countByOwnerIdAndStatus(userId, status);
    }

    @Transactional(readOnly = true)
    public long countOverdueByOwner(Long userId) {
        return taskRepository.countOverdueByOwner(userId, LocalDate.now());
    }

    // ────────────────────────────────────────────────────────────────
    //  Create
    // ────────────────────────────────────────────────────────────────

    @Transactional
    public TaskResponseDto createTask(TaskCreateDto dto, Long ownerId) {
        User owner = findUserById(ownerId);

        Task task = Task.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .dueDate(dto.getDueDate())
                .priority(dto.getPriority() != null ? dto.getPriority() : Priority.MEDIUM)
                .status(TaskStatus.TODO)
                .owner(owner)
                .tags(resolveTags(dto.getTagNames()))
                .build();

        if (dto.getAssigneeId() != null) {
            task.setAssignee(findUserById(dto.getAssigneeId()));
        }

        Task saved = taskRepository.save(task);
        auditService.log(owner.getEmail(), "TASK_CREATED", "Task", saved.getId(),
                "Task created: " + saved.getTitle());
        log.info("Task created: '{}' by user {}", saved.getTitle(), owner.getEmail());
        return toDto(saved);
    }

    // ────────────────────────────────────────────────────────────────
    //  Update
    // ────────────────────────────────────────────────────────────────

    @Transactional
    public TaskResponseDto updateTask(Long taskId, TaskUpdateDto dto, Long requestingUserId, boolean isAdmin) {
        Task task = findById(taskId);

        if (!isAdmin && !task.getOwner().getId().equals(requestingUserId)) {
            throw new UnauthorizedException("You do not have permission to edit this task.");
        }

        if (dto.getTitle()       != null) task.setTitle(dto.getTitle());
        if (dto.getDescription() != null) task.setDescription(dto.getDescription());
        if (dto.getDueDate()     != null) task.setDueDate(dto.getDueDate());
        if (dto.getPriority()    != null) task.setPriority(dto.getPriority());
        if (dto.getStatus()      != null) task.setStatus(dto.getStatus());
        if (dto.getTagNames()    != null) task.setTags(resolveTags(dto.getTagNames()));
        if (dto.getAssigneeId()  != null) task.setAssignee(findUserById(dto.getAssigneeId()));

        Task saved = taskRepository.save(task);
        auditService.log(findUserById(requestingUserId).getEmail(),
                "TASK_UPDATED", "Task", taskId, "Task updated: " + saved.getTitle());
        return toDto(saved);
    }

    // ────────────────────────────────────────────────────────────────
    //  Delete
    // ────────────────────────────────────────────────────────────────

    @Transactional
    public void deleteTask(Long taskId, Long requestingUserId, boolean isAdmin) {
        Task task = findById(taskId);

        if (!isAdmin && !task.getOwner().getId().equals(requestingUserId)) {
            throw new UnauthorizedException("You do not have permission to delete this task.");
        }

        taskRepository.delete(task);
        auditService.log(findUserById(requestingUserId).getEmail(),
                "TASK_DELETED", "Task", taskId, "Task deleted: " + task.getTitle());
        log.info("Task {} deleted", taskId);
    }

    // ────────────────────────────────────────────────────────────────
    //  Helpers
    // ────────────────────────────────────────────────────────────────

    private Task findById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task", "id", id));
    }

    private User findUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    private Set<Tag> resolveTags(Set<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) return new HashSet<>();
        Set<Tag> tags = new HashSet<>();
        for (String name : tagNames) {
            tagRepository.findByNameIgnoreCase(name.trim())
                         .ifPresent(tags::add);
        }
        return tags;
    }

    public TaskResponseDto toDto(Task task) {
        boolean overdue = task.getDueDate() != null
                && task.getDueDate().isBefore(LocalDate.now())
                && task.getStatus() != TaskStatus.DONE;

        String statusLabel = switch (task.getStatus()) {
            case TODO        -> "To Do";
            case IN_PROGRESS -> "In Progress";
            case DONE        -> "Done";
        };

        return TaskResponseDto.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .dueDate(task.getDueDate())
                .priority(task.getPriority())
                .status(task.getStatus())
                .statusLabel(statusLabel)
                .ownerUsername(task.getOwner() != null ? task.getOwner().getUsername() : null)
                .assigneeUsername(task.getAssignee() != null ? task.getAssignee().getUsername() : null)
                .tags(task.getTags() != null
                        ? task.getTags().stream().map(Tag::getName).collect(Collectors.toSet())
                        : new HashSet<>())
                .overdue(overdue)
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
