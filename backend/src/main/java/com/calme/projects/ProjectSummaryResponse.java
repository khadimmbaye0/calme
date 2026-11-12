package com.calme.projects;

import java.time.LocalDate;

public record ProjectSummaryResponse(
        Long id,
        String title,
        LocalDate deadline,
        String status,
        int totalSteps,
        int doneSteps,
        double progress) {
}
