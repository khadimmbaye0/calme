package com.calme.projects;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProjectDetailResponse(
        Long id,
        String title,
        String goal,
        LocalDate deadline,
        String status,
        BigDecimal bufferRatio,
        int totalSteps,
        int doneSteps,
        double progress,
        List<ProjectStepResponse> steps) {
}
