package com.calme.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentChatRequest(
        @NotBlank(message = "Le message est obligatoire.")
        @Size(max = 2000, message = "Le message doit faire 2000 caractères maximum.")
        String message) {
}
