package com.calme.planner;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import com.calme.blocks.BlockService;
import com.calme.blocks.BlockType;
import com.calme.calendar.EventKind;
import com.calme.calendar.EventRepository;
import com.calme.energy.EnergyLevel;
import com.calme.energy.EnergyService;
import com.calme.tasks.TaskRepository;
import com.calme.tasks.TaskService;
import com.calme.tasks.TaskStatus;
import com.calme.tasks.TaskResponse;
import com.calme.user.AppUser;
import com.calme.user.CurrentUserService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NowService {

    private final CurrentUserService currentUser;
    private final TaskRepository tasks;
    private final EventRepository events;
    private final EnergyService energy;
    private final BlockService blocks;
    private final TaskService taskService;
    private final Clock clock;

    public NowService(
            CurrentUserService currentUser,
            TaskRepository tasks,
            EventRepository events,
            EnergyService energy,
            BlockService blocks,
            TaskService taskService,
            Clock clock) {
        this.currentUser = currentUser;
        this.tasks = tasks;
        this.events = events;
        this.energy = energy;
        this.blocks = blocks;
        this.taskService = taskService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public NowResponse getNow() {
        AppUser user = currentUser.requireCurrentUser();
        ZoneId zone = ZoneId.of(user.getTimezone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(zone));
        EnergyLevel level = energy.currentToday().level();
        BlockType blockType = blocks.currentType(user, today).orElse(null);

        AvailabilityWindow window = PlannerRules.availabilityFor(today, blockType);
        List<BusyInterval> busy = fixedEvents(user, today, zone);
        List<PlannerTask> candidates = tasks
                .findByUserIdAndStatusAndDueDateLessThanEqualOrderByPriorityAscDueDateAscEstimateMaxAsc(
                        user.getId(), TaskStatus.TODO, today)
                .stream()
                .map(task -> new PlannerTask(
                        task.getId(),
                        task.getTitle(),
                        task.getEstimateMax(),
                        task.getPriority()))
                .toList();

        Optional<PlannerChoice> choice = PlannerRules.choose(
                candidates, level, today, zone, window, busy, now);

        if (choice.isPresent()) {
            PlannerChoice selected = choice.get();
            return new NowResponse(
                    TaskSummary.from(selected.task()),
                    level,
                    blockType,
                    selected.capacityMinutes(),
                    null);
        }

        int capacity = remainingCapacity(today, zone, window, busy, level, now);
        String message = candidates.isEmpty()
                ? "Rien à faire maintenant. Repose-toi."
                : "Rien ne rentre dans le temps disponible. La journée peut rester calme.";
        return new NowResponse(null, level, blockType, capacity, message);
    }

    @Transactional
    public TaskResponse start(long taskId) {
        return taskService.start(taskId);
    }

    @Transactional
    public NowResponse later(long taskId) {
        taskService.deferToTomorrow(taskId);
        NowResponse next = getNow();
        return new NowResponse(
                next.task(),
                next.energy(),
                next.blockType(),
                next.capacityMinutes(),
                "C'est reporté à demain.");
    }

    @Transactional
    public NowResponse tooHeavy(long taskId) {
        taskService.deferToTomorrow(taskId);
        NowResponse next = getNow();
        String message = next.task() != null
                ? "Je te propose autre chose."
                : "Rien d'autre ce soir. Repose-toi.";
        return new NowResponse(
                next.task(),
                next.energy(),
                next.blockType(),
                next.capacityMinutes(),
                message);
    }

    private List<BusyInterval> fixedEvents(AppUser user, LocalDate today, ZoneId zone) {
        Instant dayStart = today.atStartOfDay(zone).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant();
        return events
                .findByUserIdAndStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(
                        user.getId(), dayEnd, dayStart)
                .stream()
                .filter(event -> event.getKind() == EventKind.FIXED)
                .map(event -> new BusyInterval(event.getStartAt(), event.getEndAt()))
                .toList();
    }

    private int remainingCapacity(
            LocalDate today,
            ZoneId zone,
            AvailabilityWindow window,
            List<BusyInterval> busy,
            EnergyLevel level,
            ZonedDateTime now) {
        Instant windowStart = today.atTime(window.start()).atZone(zone).toInstant();
        Instant windowEnd = today.atTime(window.end()).atZone(zone).toInstant();
        Instant start = now.toInstant().isAfter(windowStart) ? now.toInstant() : windowStart;
        return PlannerRules.capacityMinutes(
                PlannerRules.freeMinutes(start, windowEnd, busy), level);
    }
}
