package com.calme.planner;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.calme.blocks.BlockType;
import com.calme.energy.EnergyLevel;
import com.calme.tasks.TaskPriority;

/**
 * Pure planning rules. No persistence, no LLM and no UI dependency.
 */
public final class PlannerRules {

    public static final int MAX_NOW_MINUTES = 25;
    public static final int MAX_NOW_TITLE_LENGTH = 80;

    private static final LocalTime WEEKEND_START = LocalTime.of(9, 0);
    private static final LocalTime SCHOOL_START = LocalTime.of(17, 30);
    private static final LocalTime COMPANY_START = LocalTime.of(18, 30);
    private static final LocalTime DAY_END = LocalTime.of(22, 0);

    private PlannerRules() {
    }

    public static AvailabilityWindow availabilityFor(LocalDate date, BlockType blockType) {
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return new AvailabilityWindow(WEEKEND_START, DAY_END);
        }
        if (blockType == BlockType.SCHOOL) {
            return new AvailabilityWindow(SCHOOL_START, DAY_END);
        }
        return new AvailabilityWindow(COMPANY_START, DAY_END);
    }

    public static int capacityMinutes(int freeMinutes, EnergyLevel energy) {
        if (freeMinutes <= 0) {
            return 0;
        }
        return (int) Math.floor(freeMinutes * energy.capacityRatio());
    }

    public static Optional<PlannerChoice> choose(
            List<PlannerTask> tasks,
            EnergyLevel energy,
            LocalDate date,
            ZoneId zone,
            AvailabilityWindow window,
            List<BusyInterval> busy,
            ZonedDateTime now) {

        Instant windowStart = date.atTime(window.start()).atZone(zone).toInstant();
        Instant windowEnd = date.atTime(window.end()).atZone(zone).toInstant();
        Instant start = now.toInstant().isAfter(windowStart) ? now.toInstant() : windowStart;

        if (!start.isBefore(windowEnd)) {
            return Optional.empty();
        }

        int capacity = capacityMinutes(freeMinutes(start, windowEnd, busy), energy);
        if (capacity <= 0) {
            return Optional.empty();
        }

        List<PlannerTask> eligible = tasks.stream()
                .filter(task -> task.title() != null && task.title().length() <= MAX_NOW_TITLE_LENGTH)
                .filter(task -> task.estimateMax() > 0 && task.estimateMax() <= MAX_NOW_MINUTES)
                .filter(task -> energy != EnergyLevel.LOW || task.priority() == TaskPriority.CRITICAL)
                .filter(task -> task.estimateMax() <= capacity)
                .sorted(Comparator.comparing(PlannerTask::priority)
                        .thenComparingInt(PlannerTask::estimateMax)
                        .thenComparingLong(PlannerTask::id))
                .toList();

        for (PlannerTask task : eligible) {
            Optional<TimeSlot> slot = findSlot(start, windowEnd, busy, task.estimateMax());
            if (slot.isPresent()) {
                return Optional.of(new PlannerChoice(task, slot.get(), capacity));
            }
        }

        return Optional.empty();
    }

    public static Optional<TimeSlot> findSlot(
            Instant start, Instant end, List<BusyInterval> busy, int durationMinutes) {
        if (durationMinutes <= 0 || !start.isBefore(end)) {
            return Optional.empty();
        }

        Instant cursor = start;
        for (BusyInterval interval : mergedBusy(start, end, busy)) {
            if (interval.start().isAfter(cursor)) {
                Instant candidateEnd = cursor.plus(Duration.ofMinutes(durationMinutes));
                if (!candidateEnd.isAfter(interval.start())) {
                    return Optional.of(new TimeSlot(cursor, candidateEnd));
                }
            }
            if (interval.end().isAfter(cursor)) {
                cursor = interval.end();
            }
        }

        Instant candidateEnd = cursor.plus(Duration.ofMinutes(durationMinutes));
        if (!candidateEnd.isAfter(end)) {
            return Optional.of(new TimeSlot(cursor, candidateEnd));
        }
        return Optional.empty();
    }

    public static int freeMinutes(Instant start, Instant end, List<BusyInterval> busy) {
        if (!start.isBefore(end)) {
            return 0;
        }

        int free = 0;
        Instant cursor = start;
        for (BusyInterval interval : mergedBusy(start, end, busy)) {
            if (interval.start().isAfter(cursor)) {
                free += (int) Duration.between(cursor, interval.start()).toMinutes();
            }
            if (interval.end().isAfter(cursor)) {
                cursor = interval.end();
            }
        }
        if (end.isAfter(cursor)) {
            free += (int) Duration.between(cursor, end).toMinutes();
        }
        return free;
    }

    private static List<BusyInterval> mergedBusy(Instant start, Instant end, List<BusyInterval> busy) {
        List<BusyInterval> clipped = busy.stream()
                .filter(interval -> interval.end().isAfter(start) && interval.start().isBefore(end))
                .map(interval -> new BusyInterval(
                        max(interval.start(), start),
                        min(interval.end(), end)))
                .sorted(Comparator.comparing(BusyInterval::start))
                .toList();

        List<BusyInterval> merged = new ArrayList<>();
        for (BusyInterval interval : clipped) {
            if (merged.isEmpty()) {
                merged.add(interval);
                continue;
            }
            BusyInterval last = merged.get(merged.size() - 1);
            if (interval.start().isAfter(last.end())) {
                merged.add(interval);
            } else {
                merged.set(merged.size() - 1, new BusyInterval(last.start(), max(last.end(), interval.end())));
            }
        }
        return merged;
    }

    private static Instant max(Instant left, Instant right) {
        return left.isAfter(right) ? left : right;
    }

    private static Instant min(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }
}
