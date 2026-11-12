package com.calme.tasks;

import java.time.LocalDate;

public record TaskResponse(
        Long id,
        String title,
        int estimateMin,
        int estimateMax,
        TaskPriority priority,
        TaskStatus status,
        LocalDate dueDate) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getEstimateMin(),
                task.getEstimateMax(),
                task.getPriority(),
                task.getStatus(),
                task.getDueDate());
    }
}
