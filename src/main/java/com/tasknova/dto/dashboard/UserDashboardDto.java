package com.tasknova.dto.dashboard;

import com.tasknova.dto.task.TaskResponseDto;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

/**
 * Everything the user dashboard renders, assembled in one service call.
 * Counts cover every task visible to the user (owned or assigned).
 */
@Getter
@Builder
public class UserDashboardDto {

    private long total;
    private long todo;
    private long inProgress;
    private long done;
    private long overdue;

    /** done / total, rounded, 0 when there are no tasks */
    private int completionPercent;

    /** Open tasks by priority */
    private long highOpen;
    private long mediumOpen;
    private long lowOpen;

    private List<TaskResponseDto> recent;
    private List<TaskResponseDto> dueSoon;

    /** Today and the next six days with the number of open tasks due on each */
    private List<DayLoad> week;

    public record DayLoad(LocalDate date, long count, boolean today) {}
}
