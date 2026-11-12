package com.calme.projects;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.calme.blocks.BlockService;
import com.calme.blocks.BlockType;
import com.calme.calendar.Event;
import com.calme.calendar.EventCreator;
import com.calme.calendar.EventKind;
import com.calme.calendar.EventRepository;
import com.calme.planner.AvailabilityWindow;
import com.calme.planner.BusyInterval;
import com.calme.planner.PlannerRules;
import com.calme.planner.TimeSlot;
import com.calme.tasks.Task;
import com.calme.tasks.TaskPriority;
import com.calme.tasks.TaskRepository;
import com.calme.tasks.TaskStatus;
import com.calme.user.AppUser;
import com.calme.user.CurrentUserService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private static final BigDecimal DEFAULT_BUFFER = new BigDecimal("0.200");
    private static final double PLANNING_CAPACITY_RATIO = 0.60;
    private static final long MAX_PLAN_DAYS = 366;

    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final TaskDependencyRepository dependencies;
    private final EventRepository events;
    private final CurrentUserService currentUser;
    private final BlockService blocks;
    private final Clock clock;

    public ProjectService(
            ProjectRepository projects,
            TaskRepository tasks,
            TaskDependencyRepository dependencies,
            EventRepository events,
            CurrentUserService currentUser,
            BlockService blocks,
            Clock clock) {
        this.projects = projects;
        this.tasks = tasks;
        this.dependencies = dependencies;
        this.events = events;
        this.currentUser = currentUser;
        this.blocks = blocks;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ProjectSummaryResponse> list() {
        AppUser user = currentUser.requireCurrentUser();
        return projects.findByUserIdOrderByDeadlineAsc(user.getId()).stream()
                .map(project -> summary(project, user))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectDetailResponse get(long id) {
        AppUser user = currentUser.requireCurrentUser();
        Project project = projects.findByIdAndUserId(id, user.getId())
                .orElseThrow(ProjectNotFoundException::new);
        return detail(project, user);
    }

    @Transactional
    public ProjectDetailResponse createPlan(ProjectPlanRequest request) {
        AppUser user = currentUser.requireCurrentUser();
        ZoneId zone = ZoneId.of(user.getTimezone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(zone));

        if (request.deadline().isBefore(today)) {
            throw new IllegalArgumentException("L'échéance doit être aujourd'hui ou plus tard.");
        }
        if (ChronoUnit.DAYS.between(today, request.deadline()) > MAX_PLAN_DAYS) {
            throw new IllegalArgumentException("L'échéance est trop lointaine pour un plan manuel.");
        }

        BigDecimal buffer = request.bufferRatio() == null ? DEFAULT_BUFFER : request.bufferRatio();
        if (buffer.compareTo(ProjectPlanRules.MIN_BUFFER) < 0) {
            throw new IllegalArgumentException("La marge du projet doit être d'au moins 20 %.");
        }

        List<PlanStep> steps = buildSteps(request.steps());
        List<PlanStep> ordered = ProjectPlanRules.validateAndSort(steps);
        BigDecimal required = ProjectPlanRules.requiredMinutes(steps, buffer);
        List<DayPlan> days = buildDays(user, today, request.deadline(), now, zone);
        int availableMinutes = days.stream().mapToInt(DayPlan::capacityMinutes).sum();

        if (!ProjectPlanRules.fits(required, availableMinutes)) {
            throw new ProjectPlanException(
                    "Les créneaux disponibles ne suffisent pas. Réduis le périmètre ou allonge l'échéance.");
        }

        Project project = projects.save(new Project(
                user,
                request.title().trim(),
                request.goal().trim(),
                request.deadline(),
                buffer));

        Map<Integer, Task> taskByIndex = new HashMap<>();
        for (PlanStep step : steps) {
            Task task = tasks.save(new Task(
                    user,
                    project.getId(),
                    step.title(),
                    step.estimateMin(),
                    step.estimateMax(),
                    step.priority(),
                    request.deadline()));
            taskByIndex.put(step.index(), task);
        }

        for (PlanStep step : steps) {
            Set<Integer> stepDependencies = step.dependsOn() == null
                    ? Set.of()
                    : new LinkedHashSet<>(step.dependsOn());
            for (Integer dependencyIndex : stepDependencies) {
                dependencies.save(new TaskDependency(
                        taskByIndex.get(step.index()),
                        taskByIndex.get(dependencyIndex)));
            }
        }

        Map<Integer, Instant> scheduledEnd = new HashMap<>();
        Map<LocalDate, Integer> scheduledMinutes = new HashMap<>();
        for (PlanStep step : ordered) {
            Instant earliest = now.toInstant();
            if (step.dependsOn() != null) {
                for (Integer dependencyIndex : step.dependsOn()) {
                    Instant dependencyEnd = scheduledEnd.get(dependencyIndex);
                    if (dependencyEnd != null && dependencyEnd.isAfter(earliest)) {
                        earliest = dependencyEnd;
                    }
                }
            }

            PlannedSlot slot = findSlot(days, earliest, step.estimateMax(), scheduledMinutes, zone);
            if (slot == null) {
                throw new ProjectPlanException(
                        "Les créneaux disponibles ne suffisent pas. Réduis le périmètre ou allonge l'échéance.");
            }

            Task task = taskByIndex.get(step.index());
            task.scheduleFor(slot.date());
            events.save(new Event(
                    user,
                    task,
                    step.title(),
                    slot.start(),
                    slot.end(),
                    EventKind.PLANNED,
                    EventCreator.USER));
            scheduledEnd.put(step.index(), slot.end());
            scheduledMinutes.merge(slot.date(), step.estimateMax(), Integer::sum);
            slot.day().busy().add(new BusyInterval(slot.start(), slot.end()));
        }

        return detail(project, user);
    }

    private List<PlanStep> buildSteps(List<ProjectStepRequest> requests) {
        List<PlanStep> steps = new ArrayList<>();
        for (int index = 0; index < requests.size(); index++) {
            ProjectStepRequest request = requests.get(index);
            steps.add(new PlanStep(
                    index,
                    request.title().trim(),
                    request.estimateMin(),
                    request.estimateMax(),
                    request.priority() == null ? TaskPriority.NORMAL : request.priority(),
                    request.dependsOn() == null ? List.of() : List.copyOf(request.dependsOn())));
        }
        return steps;
    }

    private List<DayPlan> buildDays(
            AppUser user, LocalDate today, LocalDate deadline, ZonedDateTime now, ZoneId zone) {
        Instant rangeStart = today.atStartOfDay(zone).toInstant();
        Instant rangeEnd = deadline.plusDays(1).atStartOfDay(zone).toInstant();
        List<Event> busyEvents = events
                .findByUserIdAndStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(
                        user.getId(), rangeEnd, rangeStart)
                .stream()
                .filter(event -> event.getKind() != EventKind.AVAILABILITY)
                .toList();

        List<DayPlan> days = new ArrayList<>();
        for (LocalDate date = today;
                !date.isAfter(deadline);
                date = date.plusDays(1)) {
            BlockType blockType = blocks.currentType(user, date).orElse(null);
            AvailabilityWindow window = PlannerRules.availabilityFor(date, blockType);
            Instant dayStart = date.atTime(window.start()).atZone(zone).toInstant();
            Instant dayEnd = date.atTime(window.end()).atZone(zone).toInstant();
            Instant effectiveStart = date.equals(today) && now.toInstant().isAfter(dayStart)
                    ? now.toInstant()
                    : dayStart;

            List<BusyInterval> busy = new ArrayList<>();
            for (Event event : busyEvents) {
                if (event.getEndAt().isAfter(effectiveStart) && event.getStartAt().isBefore(dayEnd)) {
                    busy.add(new BusyInterval(event.getStartAt(), event.getEndAt()));
                }
            }

            int freeMinutes = PlannerRules.freeMinutes(effectiveStart, dayEnd, busy);
            int capacityMinutes = (int) Math.floor(freeMinutes * PLANNING_CAPACITY_RATIO);
            days.add(new DayPlan(date, window, busy, capacityMinutes));
        }
        return days;
    }

    private PlannedSlot findSlot(
            List<DayPlan> days,
            Instant earliest,
            int durationMinutes,
            Map<LocalDate, Integer> scheduledMinutes,
            ZoneId zone) {
        for (DayPlan day : days) {
            Instant dayStart = day.date().atTime(day.window().start()).atZone(zone).toInstant();
            Instant dayEnd = day.date().atTime(day.window().end()).atZone(zone).toInstant();
            Instant start = earliest.isAfter(dayStart) ? earliest : dayStart;
            if (!start.isBefore(dayEnd)) {
                continue;
            }
            int used = scheduledMinutes.getOrDefault(day.date(), 0);
            if (used + durationMinutes > day.capacityMinutes()) {
                continue;
            }
            Optional<TimeSlot> slot = PlannerRules.findSlot(start, dayEnd, day.busy(), durationMinutes);
            if (slot.isPresent()) {
                return new PlannedSlot(day, day.date(), slot.get().start(), slot.get().end());
            }
        }
        return null;
    }

    private ProjectSummaryResponse summary(Project project, AppUser user) {
        List<Task> projectTasks = tasks.findByUserIdAndProjectIdOrderByIdAsc(user.getId(), project.getId());
        int total = projectTasks.size();
        int done = (int) projectTasks.stream()
                .filter(task -> task.getStatus() == TaskStatus.DONE)
                .count();
        double progress = total == 0 ? 0 : (double) done / total;
        return new ProjectSummaryResponse(
                project.getId(),
                project.getTitle(),
                project.getDeadline(),
                project.getStatus(),
                total,
                done,
                progress);
    }

    private ProjectDetailResponse detail(Project project, AppUser user) {
        List<Task> projectTasks = tasks.findByUserIdAndProjectIdOrderByIdAsc(user.getId(), project.getId());
        List<Long> taskIds = projectTasks.stream().map(Task::getId).toList();

        Map<Long, List<Long>> dependencyIds = new HashMap<>();
        if (!taskIds.isEmpty()) {
            for (TaskDependency dependency : dependencies.findByTaskIdIn(taskIds)) {
                dependencyIds
                        .computeIfAbsent(dependency.getTask().getId(), ignored -> new ArrayList<>())
                        .add(dependency.getDependsOnTask().getId());
            }
        }

        Map<Long, Event> scheduledEvents = new HashMap<>();
        if (!taskIds.isEmpty()) {
            for (Event event : events.findByTaskIdIn(taskIds)) {
                scheduledEvents.putIfAbsent(event.getTaskId(), event);
            }
        }

        List<ProjectStepResponse> steps = projectTasks.stream()
                .map(task -> {
                    Event scheduled = scheduledEvents.get(task.getId());
                    return new ProjectStepResponse(
                            task.getId(),
                            task.getTitle(),
                            task.getEstimateMin(),
                            task.getEstimateMax(),
                            task.getPriority(),
                            task.getStatus(),
                            task.getDueDate(),
                            scheduled == null ? null : scheduled.getStartAt(),
                            scheduled == null ? null : scheduled.getEndAt(),
                            dependencyIds.getOrDefault(task.getId(), List.of()));
                })
                .toList();

        int total = projectTasks.size();
        int done = (int) projectTasks.stream()
                .filter(task -> task.getStatus() == TaskStatus.DONE)
                .count();
        double progress = total == 0 ? 0 : (double) done / total;

        return new ProjectDetailResponse(
                project.getId(),
                project.getTitle(),
                project.getGoal(),
                project.getDeadline(),
                project.getStatus(),
                project.getBufferRatio(),
                total,
                done,
                progress,
                steps);
    }

    private record DayPlan(
            LocalDate date,
            AvailabilityWindow window,
            List<BusyInterval> busy,
            int capacityMinutes) {
    }

    private record PlannedSlot(
            DayPlan day,
            LocalDate date,
            Instant start,
            Instant end) {
    }
}
