package com.calme.planner;

import jakarta.validation.constraints.Positive;

public record NowActionRequest(
        @Positive(message = "L'identifiant de la tâche est obligatoire.")
        long taskId) {
}
