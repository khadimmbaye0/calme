package com.calme.journal;

import java.time.LocalDate;
import java.util.List;

import com.calme.energy.EnergyLevel;

public record EnergySetInverse(
        LocalDate date,
        EnergyLevel previousLevel,
        List<DeferredTask> deferredTasks) {
}
