package com.calme.planner;

public record PlannerChoice(
        PlannerTask task,
        TimeSlot slot,
        int capacityMinutes) {
}
