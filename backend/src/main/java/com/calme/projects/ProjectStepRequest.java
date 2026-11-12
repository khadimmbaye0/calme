package com.calme.projects;

import java.util.List;

import com.calme.tasks.TaskPriority;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectStepRequest(
        @NotBlank(message = "Le titre de l'étape est obligatoire.")
        @Size(max = 160, message = "Le titre de l'étape doit faire 160 caractères maximum.")
        String title,

        @Min(value = 1, message = "L'estimation minimale doit être positive.")
        int estimateMin,

        @Min(value = 1, message = "L'estimation maximale doit être positive.")
        int estimateMax,

        TaskPriority priority,

        List<Integer> dependsOn) {
}
