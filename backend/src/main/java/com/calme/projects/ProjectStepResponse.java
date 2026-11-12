package com.calme.projects;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.calme.tasks.TaskPriority;
import com.calme.tasks.TaskStatus;

public record ProjectStepResponse(
        Long taskId,
        String title,
        int estimateMin,
        int estimateMax,
        TaskPriority priority,
        TaskStatus status,
        LocalDate dueDate,
        Instant scheduledStart,
        Instant scheduledEnd,
        List<Long> dependsOnTaskIds) {
}
