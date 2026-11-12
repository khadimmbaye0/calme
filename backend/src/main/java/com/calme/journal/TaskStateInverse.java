package com.calme.journal;

import java.time.LocalDate;

import com.calme.tasks.TaskStatus;

public record TaskStateInverse(
        Long taskId,
        TaskStatus status,
        LocalDate dueDate) {
}
