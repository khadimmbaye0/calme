package com.calme.planner;

import com.calme.tasks.TaskPriority;

public record PlannerTask(
        long id,
        String title,
        int estimateMax,
        TaskPriority priority) {
}
