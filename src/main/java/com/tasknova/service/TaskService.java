package com.tasknova.service;

import com.tasknova.dto.dashboard.UserDashboardDto;
import com.tasknova.dto.tag.TagDto;
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
import com.tasknova.exception.ValidationException;
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
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Task business logic.
 *
 * <p>Access rules:
 * <ul>
 *   <li><strong>View / edit</strong> — the owner, the assignee, or any admin.</li>
 *   <li><strong>Delete</strong> — the owner or an admin.</li>
 *   <li><strong>Assign</strong> — admins only.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TagRepository  tagRepository;
    private final AuditService   auditService;

    private static final int PAGE_SIZE  = 10;
    private static final int BOARD_SIZE = 300;
    private static final int DUE_SOON_DAYS = 7;

    // ────────────────────────────────────────────────────────────────
    //  Read
    // ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<TaskResponseDto> getTasksForUser(Long userId, TaskStatus status, Priority priority, int page) {
        return getTasksForUser(userId, status, priority, null, null, page);
    }

    @Transactional(readOnly = true)
    public Page<TaskResponseDto> getTasksForUser(Long userId, TaskStatus status, Priority priority,
                                                 String query, String tag, int page) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return taskRepository.findVisibleFiltered(userId, status, priority,
                        normalize(query), normalize(tag), pageable)
                .map(this::toDto);
    }

    /** All visible tasks (capped) for the board view, most recently touched first. */
    @Transactional(readOnly = true)
    public List<TaskResponseDto> getBoardTasks(Long userId) {
        Pageable pageable = PageRequest.of(0, BOARD_SIZE, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return taskRepository.findVisible(userId, pageable).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public TaskResponseDto getTaskById(Long taskId, Long requestingUserId, boolean isAdmin) {
        Task task = findById(taskId);
        if (!canView(task, requestingUserId, isAdmin)) {
            throw new UnauthorizedException("You do not have access to this task.");
        }
        return toDto(task);
    }

    @Transactional(readOnly = true)
    public List<TaskResponseDto> getRecentTasksForUser(Long userId) {
        Pageable top5 = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt"));
        return taskRepository.findVisible(userId, top5).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public UserDashboardDto getDashboard(Long userId) {
        LocalDate today = LocalDate.now();
        long total = taskRepository.countVisible(userId);
        long done  = taskRepository.countVisibleByStatus(userId, TaskStatus.DONE);

        Map<Priority, Long> open = new EnumMap<>(Priority.class);
        for (Object[] row : taskRepository.countOpenByPriority(userId)) {
            open.put((Priority) row[0], (Long) row[1]);
        }

        List<TaskResponseDto> dueSoon = taskRepository
                .findOpenDueBy(userId, today.plusDays(DUE_SOON_DAYS), PageRequest.of(0, 6))
                .stream().map(this::toDto).toList();

        // Open tasks per day for today + the next six days
        Map<LocalDate, Long> perDay = taskRepository
                .findOpenDueBy(userId, today.plusDays(6), PageRequest.of(0, 500)).stream()
                .filter(t -> !t.getDueDate().isBefore(today))
                .collect(Collectors.groupingBy(Task::getDueDate, Collectors.counting()));
        List<UserDashboardDto.DayLoad> week = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate day = today.plusDays(i);
            week.add(new UserDashboardDto.DayLoad(day, perDay.getOrDefault(day, 0L), i == 0));
        }

        return UserDashboardDto.builder()
                .total(total)
                .todo(taskRepository.countVisibleByStatus(userId, TaskStatus.TODO))
                .inProgress(taskRepository.countVisibleByStatus(userId, TaskStatus.IN_PROGRESS))
                .done(done)
                .overdue(taskRepository.countOverdueVisible(userId, today))
                .completionPercent(percent(done, total))
                .highOpen(open.getOrDefault(Priority.HIGH, 0L))
                .mediumOpen(open.getOrDefault(Priority.MEDIUM, 0L))
                .lowOpen(open.getOrDefault(Priority.LOW, 0L))
                .recent(getRecentTasksForUser(userId))
                .dueSoon(dueSoon)
                .week(week)
                .build();
    }

    @Transactional(readOnly = true)
    public List<TagDto> getAllTags() {
        return tagRepository.findAllByOrderByNameAsc().stream().map(TagDto::from).toList();
    }

    // ────────────────────────────────────────────────────────────────
    //  Create
    // ────────────────────────────────────────────────────────────────

    @Transactional
    public TaskResponseDto createTask(TaskCreateDto dto, Long ownerId) {
        User owner = findUserById(ownerId);

        Task task = Task.builder()
                .title(dto.getTitle().trim())
                .description(blankToNull(dto.getDescription()))
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

    /** Partial update (PATCH semantics). */
    @Transactional
    public TaskResponseDto updateTask(Long taskId, TaskUpdateDto dto, Long requestingUserId, boolean isAdmin) {
        return updateTask(taskId, dto, requestingUserId, isAdmin, false);
    }

    /**
     * Updates a task.
     *
     * @param replace {@code true} for PUT semantics (see {@link TaskUpdateDto}),
     *                {@code false} to only apply non-null fields
     */
    @Transactional
    public TaskResponseDto updateTask(Long taskId, TaskUpdateDto dto, Long requestingUserId,
                                      boolean isAdmin, boolean replace) {
        Task task = findById(taskId);

        if (!canView(task, requestingUserId, isAdmin)) {
            throw new UnauthorizedException("You do not have permission to edit this task.");
        }

        TaskStatus previousStatus = task.getStatus();

        if (dto.getTitle() != null || replace) {
            if (dto.getTitle() == null || dto.getTitle().isBlank()) {
                throw new ValidationException("Task title is required.");
            }
            task.setTitle(dto.getTitle().trim());
        }
        if (replace) {
            task.setDescription(blankToNull(dto.getDescription()));
            task.setDueDate(dto.getDueDate());
            task.setTags(resolveTags(dto.getTagNames()));
            if (isAdmin) {
                task.setAssignee(dto.getAssigneeId() != null ? findUserById(dto.getAssigneeId()) : null);
            }
        } else {
            if (dto.getDescription() != null) task.setDescription(blankToNull(dto.getDescription()));
            if (dto.getDueDate()     != null) task.setDueDate(dto.getDueDate());
            if (dto.getTagNames()    != null) task.setTags(resolveTags(dto.getTagNames()));
            if (dto.getAssigneeId()  != null && isAdmin) task.setAssignee(findUserById(dto.getAssigneeId()));
        }
        if (dto.getPriority() != null) task.setPriority(dto.getPriority());
        if (dto.getStatus()   != null) task.setStatus(dto.getStatus());

        Task saved = taskRepository.save(task);

        String actor = findUserById(requestingUserId).getEmail();
        if (previousStatus != saved.getStatus()) {
            auditService.log(actor, "TASK_STATUS_CHANGED", "Task", taskId,
                    "'" + saved.getTitle() + "': " + previousStatus + " → " + saved.getStatus());
        } else {
            auditService.log(actor, "TASK_UPDATED", "Task", taskId, "Task updated: " + saved.getTitle());
        }
        return toDto(saved);
    }

    // ────────────────────────────────────────────────────────────────
    //  Delete
    // ────────────────────────────────────────────────────────────────

    @Transactional
    public void deleteTask(Long taskId, Long requestingUserId, boolean isAdmin) {
        Task task = findById(taskId);

        if (!isAdmin && !isOwner(task, requestingUserId)) {
            throw new UnauthorizedException("Only the task owner or an admin can delete this task.");
        }

        taskRepository.delete(task);
        auditService.log(findUserById(requestingUserId).getEmail(),
                "TASK_DELETED", "Task", taskId, "Task deleted: " + task.getTitle());
        log.info("Task {} deleted", taskId);
    }

    // ────────────────────────────────────────────────────────────────
    //  Access helpers
    // ────────────────────────────────────────────────────────────────

    public boolean canView(Task task, Long userId, boolean isAdmin) {
        return isAdmin || isOwner(task, userId)
                || (task.getAssignee() != null && task.getAssignee().getId().equals(userId));
    }

    private boolean isOwner(Task task, Long userId) {
        return task.getOwner() != null && task.getOwner().getId().equals(userId);
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

    /** Resolves tag names to existing tags; rejects unknown names instead of silently dropping them. */
    private Set<Tag> resolveTags(Set<String> tagNames) {
        Set<Tag> tags = new HashSet<>();
        if (tagNames == null) return tags;
        for (String raw : tagNames) {
            if (raw == null || raw.isBlank()) continue;
            String name = raw.trim().replaceFirst("^#", "");
            Tag tag = tagRepository.findByNameIgnoreCase(name)
                    .orElseThrow(() -> new ValidationException("Unknown tag: '" + name + "'."));
            tags.add(tag);
        }
        return tags;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    static int percent(long part, long whole) {
        return whole == 0 ? 0 : (int) Math.round(part * 100.0 / whole);
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

        List<TagDto> tagDetails = task.getTags() == null ? List.of()
                : task.getTags().stream()
                    .map(TagDto::from)
                    .sorted(Comparator.comparing(TagDto::name))
                    .toList();

        return TaskResponseDto.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .dueDate(task.getDueDate())
                .priority(task.getPriority())
                .status(task.getStatus())
                .statusLabel(statusLabel)
                .ownerId(task.getOwner() != null ? task.getOwner().getId() : null)
                .ownerUsername(task.getOwner() != null ? task.getOwner().getUsername() : null)
                .assigneeId(task.getAssignee() != null ? task.getAssignee().getId() : null)
                .assigneeUsername(task.getAssignee() != null ? task.getAssignee().getUsername() : null)
                .tags(tagDetails.stream().map(TagDto::name).collect(Collectors.toCollection(LinkedHashSet::new)))
                .tagDetails(tagDetails)
                .overdue(overdue)
                .dueInDays(task.getDueDate() == null ? null
                        : ChronoUnit.DAYS.between(LocalDate.now(), task.getDueDate()))
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
