package com.calme.projects;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProjectPlanRequest(
        @NotBlank(message = "Le titre du projet est obligatoire.")
        @Size(max = 160, message = "Le titre du projet doit faire 160 caractères maximum.")
        String title,

        @NotBlank(message = "L'objectif du projet est obligatoire.")
        @Size(max = 2000, message = "L'objectif doit faire 2000 caractères maximum.")
        String goal,

        @NotNull(message = "L'échéance est obligatoire.")
        LocalDate deadline,

        BigDecimal bufferRatio,

        @NotEmpty(message = "Le projet doit contenir au moins une étape.")
        List<@Valid ProjectStepRequest> steps) {
}
