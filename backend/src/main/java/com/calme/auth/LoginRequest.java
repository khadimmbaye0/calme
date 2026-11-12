package com.calme.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Le mot de passe est obligatoire.")
        String password) {
}
