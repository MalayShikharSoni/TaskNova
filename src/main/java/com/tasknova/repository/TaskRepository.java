package com.tasknova.repository;

import com.tasknova.entity.Task;
import com.tasknova.entity.enums.Priority;
import com.tasknova.entity.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Data access layer for {@link Task} entities.
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    // ────────────────────────────────────────────────────────────────
    //  Owner-scoped queries (User portal)
    // ────────────────────────────────────────────────────────────────

    Page<Task> findAllByOwnerId(Long ownerId, Pageable pageable);

    Page<Task> findAllByOwnerIdAndStatus(Long ownerId, TaskStatus status, Pageable pageable);

    Page<Task> findAllByOwnerIdAndPriority(Long ownerId, Priority priority, Pageable pageable);

    /** Tasks the user owns OR is assigned to */
    @Query("""
            SELECT t FROM Task t
            WHERE t.owner.id = :userId OR t.assignee.id = :userId
            """)
    Page<Task> findAllByUserInvolved(@Param("userId") Long userId, Pageable pageable);

    // ────────────────────────────────────────────────────────────────
    //  Admin-scoped queries
    // ────────────────────────────────────────────────────────────────

    Page<Task> findAllByAssigneeId(Long assigneeId, Pageable pageable);

    /** Full-text style search across title and description (admin) */
    @Query("""
            SELECT t FROM Task t
            WHERE LOWER(t.title)       LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(t.description) LIKE LOWER(CONCAT('%', :query, '%'))
            """)
    Page<Task> searchTasks(@Param("query") String query, Pageable pageable);

    /** Filter by owner and optional status + priority */
    @Query("""
            SELECT t FROM Task t
            WHERE t.owner.id = :ownerId
              AND (:status   IS NULL OR t.status   = :status)
              AND (:priority IS NULL OR t.priority = :priority)
            """)
    Page<Task> findByOwnerFiltered(
            @Param("ownerId") Long ownerId,
            @Param("status") TaskStatus status,
            @Param("priority") Priority priority,
            Pageable pageable
    );

    // ────────────────────────────────────────────────────────────────
    //  Statistics (dashboard counts)
    // ────────────────────────────────────────────────────────────────

    long countByOwnerId(Long ownerId);

    long countByOwnerIdAndStatus(Long ownerId, TaskStatus status);

    long countByStatus(TaskStatus status);

    /** Tasks overdue: dueDate < today AND status != DONE */
    @Query("""
            SELECT COUNT(t) FROM Task t
            WHERE t.owner.id = :ownerId
              AND t.dueDate < :today
              AND t.status  <> com.tasknova.entity.enums.TaskStatus.DONE
            """)
    long countOverdueByOwner(@Param("ownerId") Long ownerId, @Param("today") LocalDate today);

    /** System-wide overdue count (admin dashboard) */
    @Query("""
            SELECT COUNT(t) FROM Task t
            WHERE t.dueDate < :today
              AND t.status  <> com.tasknova.entity.enums.TaskStatus.DONE
            """)
    long countOverdueAll(@Param("today") LocalDate today);

    /** Recent tasks for dashboard widget */
    @Query("""
            SELECT t FROM Task t
            WHERE t.owner.id = :ownerId
            ORDER BY t.createdAt DESC
            """)
    List<Task> findTop5ByOwnerIdOrderByCreatedAtDesc(@Param("ownerId") Long ownerId, Pageable pageable);
}
