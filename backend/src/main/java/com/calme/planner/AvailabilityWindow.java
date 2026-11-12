package com.calme.planner;

import java.time.LocalTime;

public record AvailabilityWindow(LocalTime start, LocalTime end) {
}
