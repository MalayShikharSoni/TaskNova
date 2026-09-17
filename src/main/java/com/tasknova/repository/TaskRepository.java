package com.tasknova.repository;

import com.tasknova.entity.Task;
import com.tasknova.entity.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    // ── Owner-scoped queries ──────────────────────────────────

    Page<Task> findByOwnerId(Long ownerId, Pageable pageable);

    long countByOwnerId(Long ownerId);

    long countByOwnerIdAndStatus(Long ownerId, TaskStatus status);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.owner.id = :ownerId " +
           "AND t.dueDate < :today AND t.status <> 'DONE'")
    long countOverdueByOwner(@Param("ownerId") Long ownerId, @Param("today") LocalDate today);

    /** 5 most recent tasks for the dashboard widget */
    List<Task> findTop5ByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    /** Owner-scoped filtered query with optional status and priority */
    @Query("SELECT t FROM Task t WHERE t.owner.id = :ownerId " +
           "AND (:status   IS NULL OR t.status   = :status) " +
           "AND (:priority IS NULL OR t.priority = :priority)")
    Page<Task> findByOwnerFiltered(
        @Param("ownerId")  Long ownerId,
        @Param("status")   com.tasknova.entity.enums.TaskStatus status,
        @Param("priority") com.tasknova.entity.enums.Priority   priority,
        Pageable pageable
    );

    // ── System-wide queries (admin) ───────────────────────────

    long countByStatus(TaskStatus status);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.dueDate < :today AND t.status <> 'DONE'")
    long countOverdueAll(@Param("today") LocalDate today);

    /** System-wide full-text search */
    @Query("SELECT t FROM Task t WHERE " +
           "LOWER(t.title)       LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(t.description) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<Task> searchTasks(@Param("q") String query, Pageable pageable);
}
