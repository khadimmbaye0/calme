package com.calme.projects;

import java.util.List;

import com.calme.tasks.TaskPriority;

public record PlanStep(
        int index,
        String title,
        int estimateMin,
        int estimateMax,
        TaskPriority priority,
        List<Integer> dependsOn) {
}
