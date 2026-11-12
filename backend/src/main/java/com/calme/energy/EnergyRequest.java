package com.calme.energy;

import jakarta.validation.constraints.NotNull;

public record EnergyRequest(
        @NotNull(message = "Le niveau d'énergie est obligatoire.")
        EnergyLevel level) {
}
