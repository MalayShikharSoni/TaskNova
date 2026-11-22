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
 * Task persistence.
 *
 * <p>"Visible" queries return tasks a user either <em>owns</em> or has been
 * <em>assigned</em> by an admin — both show up in that user's workspace.
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    String VISIBLE = "(t.owner.id = :uid OR a.id = :uid)";

    // ── Owner-scoped (used by admin views & seeding) ──────────

    Page<Task> findByOwnerId(Long ownerId, Pageable pageable);

    long countByOwnerId(Long ownerId);

    // ── Visible-to-user queries (owner OR assignee) ───────────

    /**
     * Filtered, searchable task list for a user.
     * {@code q} and {@code tag} use the empty string to mean "no filter".
     */
    @Query("SELECT t FROM Task t LEFT JOIN t.assignee a WHERE " + VISIBLE +
           " AND (:status IS NULL OR t.status = :status)" +
           " AND (:priority IS NULL OR t.priority = :priority)" +
           " AND (:q = '' OR LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%'))" +
           "      OR LOWER(COALESCE(t.description, '')) LIKE LOWER(CONCAT('%', :q, '%')))" +
           " AND (:tag = '' OR EXISTS (SELECT 1 FROM Task t2 JOIN t2.tags tg" +
           "      WHERE t2.id = t.id AND LOWER(tg.name) = LOWER(:tag)))")
    Page<Task> findVisibleFiltered(
        @Param("uid")      Long uid,
        @Param("status")   TaskStatus status,
        @Param("priority") Priority priority,
        @Param("q")        String q,
        @Param("tag")      String tag,
        Pageable pageable
    );

    @Query("SELECT COUNT(t) FROM Task t LEFT JOIN t.assignee a WHERE " + VISIBLE)
    long countVisible(@Param("uid") Long uid);

    @Query("SELECT COUNT(t) FROM Task t LEFT JOIN t.assignee a WHERE " + VISIBLE +
           " AND t.status = :status")
    long countVisibleByStatus(@Param("uid") Long uid, @Param("status") TaskStatus status);

    @Query("SELECT COUNT(t) FROM Task t LEFT JOIN t.assignee a WHERE " + VISIBLE +
           " AND t.dueDate < :today AND t.status <> com.tasknova.entity.enums.TaskStatus.DONE")
    long countOverdueVisible(@Param("uid") Long uid, @Param("today") LocalDate today);

    /** Open tasks due between {@code from} and {@code until} (inclusive), soonest first. */
    @Query("SELECT t FROM Task t LEFT JOIN t.assignee a WHERE " + VISIBLE +
           " AND t.status <> com.tasknova.entity.enums.TaskStatus.DONE" +
           " AND t.dueDate IS NOT NULL AND t.dueDate <= :until" +
           " ORDER BY t.dueDate ASC")
    List<Task> findOpenDueBy(@Param("uid") Long uid, @Param("until") LocalDate until, Pageable pageable);

    @Query("SELECT t FROM Task t LEFT JOIN t.assignee a WHERE " + VISIBLE)
    List<Task> findVisible(@Param("uid") Long uid, Pageable pageable);

    /** Open (not done) task counts grouped by priority. Rows are {@code [Priority, Long]}. */
    @Query("SELECT t.priority, COUNT(t) FROM Task t LEFT JOIN t.assignee a WHERE " + VISIBLE +
           " AND t.status <> com.tasknova.entity.enums.TaskStatus.DONE GROUP BY t.priority")
    List<Object[]> countOpenByPriority(@Param("uid") Long uid);

    // ── System-wide queries (admin) ───────────────────────────

    long countByStatus(TaskStatus status);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.dueDate < :today" +
           " AND t.status <> com.tasknova.entity.enums.TaskStatus.DONE")
    long countOverdueAll(@Param("today") LocalDate today);

    /** System-wide search with optional status filter. {@code q} = '' means no search. */
    @Query("SELECT t FROM Task t WHERE " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:q = '' OR LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           " LOWER(COALESCE(t.description, '')) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Task> searchTasks(@Param("q") String query, @Param("status") TaskStatus status, Pageable pageable);
}
