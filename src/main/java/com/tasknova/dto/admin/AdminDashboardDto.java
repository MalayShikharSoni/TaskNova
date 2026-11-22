package com.tasknova.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Aggregated statistics shown on the Admin dashboard.
 */
@Data
@Builder
public class AdminDashboardDto {

    // ── User stats ──
    private long totalUsers;
    private long activeUsers;
    private long totalAdmins;

    // ── Task stats ──
    private long totalTasks;
    private long todoTasks;
    private long inProgressTasks;
    private long doneTasks;
    private long overdueTasks;

    /** done / total, rounded */
    private int completionPercent;

    /** Latest audit entries for the activity feed */
    private List<AuditLogDto> recentActivity;
}
