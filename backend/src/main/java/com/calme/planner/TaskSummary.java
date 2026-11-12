package com.calme.planner;

import com.calme.tasks.TaskPriority;

public record TaskSummary(
        Long id,
        String title,
        int estimateMax,
        TaskPriority priority) {

    public static TaskSummary from(PlannerTask task) {
        return new TaskSummary(task.id(), task.title(), task.estimateMax(), task.priority());
    }
}
