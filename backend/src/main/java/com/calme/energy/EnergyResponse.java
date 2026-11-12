package com.calme.energy;

import java.time.LocalDate;

public record EnergyResponse(
        LocalDate date,
        EnergyLevel level,
        int deferredCount) {
}
