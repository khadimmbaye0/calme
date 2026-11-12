package com.calme.planner;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import com.calme.blocks.BlockType;
import com.calme.energy.EnergyLevel;
import com.calme.tasks.TaskPriority;

import org.junit.jupiter.api.Test;

class PlannerRulesTest {

    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final LocalDate THURSDAY = LocalDate.of(2026, 11, 12);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 11, 14);
    private static final ZonedDateTime NOON = THURSDAY.atTime(12, 0).atZone(UTC);

    @Test
    void capacityFollowsEnergyLevel() {
        assertThat(PlannerRules.capacityMinutes(120, EnergyLevel.GOOD)).isEqualTo(72);
        assertThat(PlannerRules.capacityMinutes(120, EnergyLevel.MID)).isEqualTo(48);
        assertThat(PlannerRules.capacityMinutes(120, EnergyLevel.LOW)).isEqualTo(36);
        assertThat(PlannerRules.capacityMinutes(0, EnergyLevel.GOOD)).isZero();
    }

    @Test
    void availabilityUsesBlockTypeAndWeekend() {
        assertThat(PlannerRules.availabilityFor(THURSDAY, BlockType.SCHOOL))
                .isEqualTo(new AvailabilityWindow(LocalTime.of(17, 30), LocalTime.of(22, 0)));
        assertThat(PlannerRules.availabilityFor(THURSDAY, BlockType.COMPANY))
                .isEqualTo(new AvailabilityWindow(LocalTime.of(18, 30), LocalTime.of(22, 0)));
        assertThat(PlannerRules.availabilityFor(SATURDAY, BlockType.SCHOOL))
                .isEqualTo(new AvailabilityWindow(LocalTime.of(9, 0), LocalTime.of(22, 0)));
    }

    @Test
    void findSlotSkipsFixedEvents() {
        Instant start = THURSDAY.atTime(17, 30).atZone(UTC).toInstant();
        Instant end = THURSDAY.atTime(22, 0).atZone(UTC).toInstant();
        BusyInterval fixed = new BusyInterval(
                THURSDAY.atTime(18, 0).atZone(UTC).toInstant(),
                THURSDAY.atTime(19, 0).atZone(UTC).toInstant());

        Optional<TimeSlot> slot = PlannerRules.findSlot(start, end, List.of(fixed), 60);

        assertThat(slot).isPresent();
        assertThat(slot.get().start()).isEqualTo(THURSDAY.atTime(19, 0).atZone(UTC).toInstant());
        assertThat(slot.get().end()).isEqualTo(THURSDAY.atTime(20, 0).atZone(UTC).toInstant());
    }

    @Test
    void choosePrefersCriticalTasks() {
        List<PlannerTask> tasks = List.of(
                new PlannerTask(1, "Normale", 15, TaskPriority.NORMAL),
                new PlannerTask(2, "Critique", 10, TaskPriority.CRITICAL),
                new PlannerTask(3, "Optionnelle", 20, TaskPriority.OPTIONAL));

        Optional<PlannerChoice> choice = PlannerRules.choose(
                tasks,
                EnergyLevel.GOOD,
                THURSDAY,
                UTC,
                PlannerRules.availabilityFor(THURSDAY, BlockType.SCHOOL),
                List.of(),
                NOON);

        assertThat(choice).isPresent();
        assertThat(choice.get().task().title()).isEqualTo("Critique");
        assertThat(choice.get().slot().start())
                .isEqualTo(THURSDAY.atTime(17, 30).atZone(UTC).toInstant());
    }

    @Test
    void lowEnergyKeepsOnlyCriticalTasks() {
        List<PlannerTask> tasks = List.of(
                new PlannerTask(1, "Normale", 15, TaskPriority.NORMAL),
                new PlannerTask(2, "Critique", 10, TaskPriority.CRITICAL));

        Optional<PlannerChoice> choice = PlannerRules.choose(
                tasks,
                EnergyLevel.LOW,
                THURSDAY,
                UTC,
                PlannerRules.availabilityFor(THURSDAY, BlockType.SCHOOL),
                List.of(),
                NOON);

        assertThat(choice).isPresent();
        assertThat(choice.get().task().title()).isEqualTo("Critique");
    }

    @Test
    void fixedEventCoveringTheWindowLeavesNoSlot() {
        Instant start = THURSDAY.atTime(17, 30).atZone(UTC).toInstant();
        Instant end = THURSDAY.atTime(22, 0).atZone(UTC).toInstant();
        BusyInterval fixed = new BusyInterval(start, end);

        Optional<TimeSlot> slot = PlannerRules.findSlot(start, end, List.of(fixed), 15);

        assertThat(slot).isEmpty();
    }

    @Test
    void capacityLimitsTheSelectedTask() {
        Instant busyStart = THURSDAY.atTime(18, 0).atZone(UTC).toInstant();
        Instant busyEnd = THURSDAY.atTime(22, 0).atZone(UTC).toInstant();
        List<PlannerTask> tasks = List.of(
                new PlannerTask(1, "Trop longue", 20, TaskPriority.NORMAL),
                new PlannerTask(2, "Courte", 15, TaskPriority.NORMAL));

        Optional<PlannerChoice> choice = PlannerRules.choose(
                tasks,
                EnergyLevel.GOOD,
                THURSDAY,
                UTC,
                PlannerRules.availabilityFor(THURSDAY, BlockType.SCHOOL),
                List.of(new BusyInterval(busyStart, busyEnd)),
                NOON);

        assertThat(choice).isPresent();
        assertThat(choice.get().task().title()).isEqualTo("Courte");
    }

    @Test
    void rejectsLongTitlesAndTasksLongerThanTwentyFiveMinutes() {
        List<PlannerTask> tasks = List.of(
                new PlannerTask(1, "x".repeat(81), 10, TaskPriority.CRITICAL),
                new PlannerTask(2, "Trop longue", 30, TaskPriority.CRITICAL),
                new PlannerTask(3, "Valide", 15, TaskPriority.NORMAL));

        Optional<PlannerChoice> choice = PlannerRules.choose(
                tasks,
                EnergyLevel.GOOD,
                THURSDAY,
                UTC,
                PlannerRules.availabilityFor(THURSDAY, BlockType.SCHOOL),
                List.of(),
                NOON);

        assertThat(choice).isPresent();
        assertThat(choice.get().task().title()).isEqualTo("Valide");
    }
}
