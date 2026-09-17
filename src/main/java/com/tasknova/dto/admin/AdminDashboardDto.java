package com.tasknova.dto.admin;

import lombok.Builder;
import lombok.Data;

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
}
