package com.calme.calendar;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EventRequest(
        @NotBlank(message = "Le titre est obligatoire.")
        @Size(max = 160, message = "Le titre doit faire 160 caractères maximum.")
        String title,

        @NotNull(message = "L'heure de début est obligatoire.")
        Instant startAt,

        @NotNull(message = "L'heure de fin est obligatoire.")
        Instant endAt,

        @NotNull(message = "Le type d'événement est obligatoire.")
        EventKind kind) {
}
