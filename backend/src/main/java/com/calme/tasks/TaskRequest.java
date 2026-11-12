package com.calme.tasks;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TaskRequest(
        @NotBlank(message = "Le titre est obligatoire.")
        @Size(max = 160, message = "Le titre doit faire 160 caractères maximum.")
        String title,

        @Min(value = 1, message = "L'estimation minimale doit être positive.")
        int estimateMin,

        @Min(value = 1, message = "L'estimation maximale doit être positive.")
        int estimateMax,

        @NotNull(message = "La priorité est obligatoire.")
        TaskPriority priority,

        LocalDate dueDate) {
}
