package com.calme.journal;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import com.calme.calendar.Event;
import com.calme.calendar.EventRepository;
import com.calme.energy.EnergyLog;
import com.calme.energy.EnergyLogRepository;
import com.calme.energy.EnergySource;
import com.calme.projects.Project;
import com.calme.projects.ProjectRepository;
import com.calme.projects.TaskDependencyRepository;
import com.calme.tasks.Task;
import com.calme.tasks.TaskRepository;
import com.calme.user.AppUser;
import com.calme.user.CurrentUserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UndoService {

    private final CurrentUserService currentUser;
    private final EventRepository events;
    private final TaskRepository tasks;
    private final ProjectRepository projects;
    private final TaskDependencyRepository dependencies;
    private final EnergyLogRepository energyLogs;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public UndoService(
            CurrentUserService currentUser,
            EventRepository events,
            TaskRepository tasks,
            ProjectRepository projects,
            TaskDependencyRepository dependencies,
            EnergyLogRepository energyLogs,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.currentUser = currentUser;
        this.events = events;
        this.tasks = tasks;
        this.projects = projects;
        this.dependencies = dependencies;
        this.energyLogs = energyLogs;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void undo(AgentAction action) {
        AppUser user = currentUser.requireCurrentUser();
        switch (action.getIntent()) {
            case "event.create" -> undoEventCreate(user, read(action, EventCreateInverse.class));
            case "event.update" -> undoEventUpdate(user, read(action, EventUpdateInverse.class));
            case "event.delete" -> undoEventDelete(user, read(action, EventDeleteInverse.class));
            case "task.create" -> undoTaskCreate(user, read(action, TaskCreateInverse.class));
            case "task.start", "task.done", "task.defer" ->
                    undoTaskState(user, read(action, TaskStateInverse.class));
            case "project.plan" -> undoProjectPlan(user, read(action, ProjectPlanInverse.class));
            case "energy.set" -> undoEnergySet(user, read(action, EnergySetInverse.class));
            default -> throw new JournalStateException("Cette action ne peut pas être annulée.");
        }
    }

    private void undoEventCreate(AppUser user, EventCreateInverse inverse) {
        Event event = events.findByIdAndUserId(inverse.eventId(), user.getId())
                .orElseThrow(() -> new JournalStateException("L'état à restaurer n'existe plus."));
        events.delete(event);
    }

    private void undoEventUpdate(AppUser user, EventUpdateInverse inverse) {
        Event event = events.findByIdAndUserId(inverse.eventId(), user.getId())
                .orElseThrow(() -> new JournalStateException("L'état à restaurer n'existe plus."));
        event.update(inverse.title(), inverse.startAt(), inverse.endAt(), inverse.kind());
    }

    private void undoEventDelete(AppUser user, EventDeleteInverse inverse) {
        jdbcTemplate.update("""
                insert into event (
                    id, user_id, task_id, title, start_at, end_at,
                    kind, recurrence_rule, created_by
                ) values (?, ?, ?, ?, ?, ?, ?, null, ?)
                """,
                inverse.eventId(),
                user.getId(),
                inverse.taskId(),
                inverse.title(),
                OffsetDateTime.ofInstant(inverse.startAt(), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(inverse.endAt(), ZoneOffset.UTC),
                inverse.kind().name(),
                inverse.createdBy().name());
        jdbcTemplate.queryForObject(
                "select setval(pg_get_serial_sequence('event', 'id'), (select max(id) from event))",
                Long.class);
    }

    private void undoTaskCreate(AppUser user, TaskCreateInverse inverse) {
        Task task = tasks.findByIdAndUserId(inverse.taskId(), user.getId())
                .orElseThrow(() -> new JournalStateException("L'état à restaurer n'existe plus."));
        tasks.delete(task);
    }

    private void undoTaskState(AppUser user, TaskStateInverse inverse) {
        Task task = tasks.findByIdAndUserId(inverse.taskId(), user.getId())
                .orElseThrow(() -> new JournalStateException("L'état à restaurer n'existe plus."));
        task.restoreState(inverse.status(), inverse.dueDate());
    }

    private void undoProjectPlan(AppUser user, ProjectPlanInverse inverse) {
        Project project = projects.findByIdAndUserId(inverse.projectId(), user.getId())
                .orElseThrow(() -> new JournalStateException("L'état à restaurer n'existe plus."));
        List<Task> projectTasks = tasks.findByUserIdAndProjectIdOrderByIdAsc(user.getId(), project.getId());
        List<Long> taskIds = projectTasks.stream().map(Task::getId).toList();

        if (!taskIds.isEmpty()) {
            events.deleteAll(events.findByTaskIdIn(taskIds));
            dependencies.deleteAll(dependencies.findByTaskIdIn(taskIds));
            tasks.deleteAll(projectTasks);
        }
        projects.delete(project);
    }

    private void undoEnergySet(AppUser user, EnergySetInverse inverse) {
        List<DeferredTask> deferred = inverse.deferredTasks() == null
                ? new ArrayList<>()
                : inverse.deferredTasks();
        for (DeferredTask snapshot : deferred) {
            Task task = tasks.findByIdAndUserId(snapshot.taskId(), user.getId())
                    .orElseThrow(() -> new JournalStateException("L'état à restaurer n'existe plus."));
            task.restoreState(snapshot.status(), snapshot.dueDate());
        }
        energyLogs.save(new EnergyLog(user, inverse.date(), inverse.previousLevel(), EnergySource.TAP));
    }

    private <T> T read(AgentAction action, Class<T> type) {
        if (action.getInversePayload() == null) {
            throw new JournalStateException("Cette action ne peut pas être annulée.");
        }
        try {
            return objectMapper.treeToValue(action.getInversePayload(), type);
        } catch (JsonProcessingException exception) {
            throw new JournalStateException("Cette action ne peut pas être annulée.");
        }
    }
}
