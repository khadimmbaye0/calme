package com.calme.agent;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.calme.calendar.CalendarService;
import com.calme.calendar.EventKind;
import com.calme.calendar.EventRequest;
import com.calme.calendar.EventResponse;
import com.calme.energy.EnergyLevel;
import com.calme.energy.EnergyService;
import com.calme.journal.JournalResponse;
import com.calme.journal.JournalService;
import com.calme.planner.NowResponse;
import com.calme.planner.NowService;
import com.calme.projects.ProjectDetailResponse;
import com.calme.projects.ProjectPlanRequest;
import com.calme.projects.ProjectService;
import com.calme.projects.ProjectStepRequest;
import com.calme.tasks.TaskPriority;
import com.calme.tasks.TaskRequest;
import com.calme.tasks.TaskService;
import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Service;

@Service
public class AgentToolExecutor {

    private final CalendarService calendar;
    private final TaskService tasks;
    private final ProjectService projects;
    private final EnergyService energy;
    private final NowService now;
    private final JournalService journal;

    public AgentToolExecutor(
            CalendarService calendar,
            TaskService tasks,
            ProjectService projects,
            EnergyService energy,
            NowService now,
            JournalService journal) {
        this.calendar = calendar;
        this.tasks = tasks;
        this.projects = projects;
        this.energy = energy;
        this.now = now;
        this.journal = journal;
    }

    public ToolExecution execute(String name, JsonNode args) {
        return switch (name) {
            case "calendar_delete" -> confirmDelete(args);
            case "project_plan" -> confirmLargeProject(args);
            default -> executeDirect(name, args);
        };
    }

    public ToolExecution executeDirect(String name, JsonNode args) {
        return switch (name) {
            case "calendar_list" -> calendarList(args);
            case "calendar_create" -> calendarCreate(args);
            case "calendar_update" -> calendarUpdate(args);
            case "calendar_delete" -> calendarDelete(args);
            case "task_list" -> taskList();
            case "task_create" -> taskCreate(args);
            case "task_start" -> taskStart(args);
            case "task_complete" -> taskComplete(args);
            case "task_defer" -> taskDefer(args);
            case "project_list" -> projectList();
            case "project_plan" -> projectPlan(args);
            case "energy_set" -> energySet(args);
            case "now_get" -> nowGet();
            case "journal_list" -> journalList();
            case "journal_undo" -> journalUndo(args);
            default -> throw new ToolInputException("Cet outil n'est pas disponible.");
        };
    }

    private ToolExecution confirmDelete(JsonNode args) {
        EventResponse event = calendar.get(requiredLong(args, "event_id"));
        return ToolExecution.confirm("Supprimer « " + event.title() + " » du calendrier ?");
    }

    private ToolExecution confirmLargeProject(JsonNode args) {
        JsonNode steps = args == null ? null : args.get("steps");
        if (steps == null || !steps.isArray() || steps.isEmpty()) {
            throw new ToolInputException("Le projet doit contenir au moins une étape.");
        }
        int elements = steps.size() * 2 + 1;
        if (elements > 10) {
            String title = requiredText(args, "title");
            return ToolExecution.confirm(
                    "Planifier « " + title + " » avec " + steps.size()
                            + " étapes ? Cela crée " + elements + " éléments.");
        }
        return projectPlan(args);
    }

    private ToolExecution calendarList(JsonNode args) {
        Instant from = requiredInstant(args, "from");
        Instant to = requiredInstant(args, "to");
        List<EventResponse> events = calendar.list(from, to);
        return ToolExecution.ok(events.size() + " événement(s).", events);
    }

    private ToolExecution calendarCreate(JsonNode args) {
        EventResponse event = calendar.create(new EventRequest(
                requiredText(args, "title"),
                requiredInstant(args, "start_at"),
                requiredInstant(args, "end_at"),
                requiredKind(args, "kind")));
        return ToolExecution.ok("Événement créé : « " + event.title() + " ».", event);
    }

    private ToolExecution calendarUpdate(JsonNode args) {
        long id = requiredLong(args, "event_id");
        EventResponse current = calendar.get(id);
        EventResponse updated = calendar.update(id, new EventRequest(
                optionalText(args, "title", current.title()),
                optionalInstant(args, "start_at", current.startAt()),
                optionalInstant(args, "end_at", current.endAt()),
                args != null && args.hasNonNull("kind") ? requiredKind(args, "kind") : current.kind()));
        return ToolExecution.ok("Événement modifié : « " + updated.title() + " ».", updated);
    }

    private ToolExecution calendarDelete(JsonNode args) {
        long id = requiredLong(args, "event_id");
        EventResponse event = calendar.get(id);
        calendar.delete(id);
        return ToolExecution.ok("Événement supprimé : « " + event.title() + " ».", null);
    }

    private ToolExecution taskList() {
        var open = tasks.listOpen();
        return ToolExecution.ok(open.size() + " tâche(s) ouverte(s).", open);
    }

    private ToolExecution taskCreate(JsonNode args) {
        var task = tasks.create(new TaskRequest(
                requiredText(args, "title"),
                requiredInt(args, "estimate_min"),
                requiredInt(args, "estimate_max"),
                requiredPriority(args, "priority"),
                optionalDate(args, "due_date")));
        return ToolExecution.ok("Tâche ajoutée : « " + task.title() + " ».", task);
    }

    private ToolExecution taskStart(JsonNode args) {
        var task = tasks.start(requiredLong(args, "task_id"));
        return ToolExecution.ok("Tâche démarrée : « " + task.title() + " ».", task);
    }

    private ToolExecution taskComplete(JsonNode args) {
        tasks.done(requiredLong(args, "task_id"));
        return ToolExecution.ok("Tâche terminée.", null);
    }

    private ToolExecution taskDefer(JsonNode args) {
        tasks.deferToTomorrow(requiredLong(args, "task_id"));
        return ToolExecution.ok("Tâche reportée à demain.", null);
    }

    private ToolExecution projectList() {
        var projects = this.projects.list();
        return ToolExecution.ok(projects.size() + " projet(s).", projects);
    }

    private ToolExecution projectPlan(JsonNode args) {
        ProjectDetailResponse project = projects.createPlan(projectPlanRequest(args));
        return ToolExecution.ok(
                "Projet planifié : « " + project.title() + " » (" + project.totalSteps() + " étapes).",
                project);
    }

    private ProjectPlanRequest projectPlanRequest(JsonNode args) {
        JsonNode steps = args == null ? null : args.get("steps");
        if (steps == null || !steps.isArray() || steps.isEmpty()) {
            throw new ToolInputException("Le projet doit contenir au moins une étape.");
        }

        Map<String, Integer> indexes = new LinkedHashMap<>();
        List<JsonNode> stepNodes = new ArrayList<>();
        int index = 0;
        for (JsonNode step : steps) {
            String ref = requiredText(step, "ref");
            if (indexes.putIfAbsent(ref, index) != null) {
                throw new ToolInputException("Deux étapes utilisent la même référence.");
            }
            stepNodes.add(step);
            index++;
        }

        List<ProjectStepRequest> requests = new ArrayList<>();
        for (JsonNode step : stepNodes) {
            List<Integer> dependencies = new ArrayList<>();
            JsonNode dependsOn = step.get("depends_on");
            if (dependsOn != null && dependsOn.isArray()) {
                for (JsonNode dependency : dependsOn) {
                    String ref = dependency.asText();
                    Integer dependencyIndex = indexes.get(ref);
                    if (dependencyIndex == null) {
                        throw new ToolInputException("Une dépendance référence une étape inconnue.");
                    }
                    dependencies.add(dependencyIndex);
                }
            }
            requests.add(new ProjectStepRequest(
                    requiredText(step, "title"),
                    requiredInt(step, "estimate_min"),
                    requiredInt(step, "estimate_max"),
                    step.hasNonNull("priority") ? requiredPriority(step, "priority") : TaskPriority.NORMAL,
                    dependencies));
        }

        BigDecimal buffer = args.has("buffer_ratio")
                ? BigDecimal.valueOf(args.get("buffer_ratio").asDouble())
                : new BigDecimal("0.20");
        return new ProjectPlanRequest(
                requiredText(args, "title"),
                requiredText(args, "goal"),
                requiredDate(args, "deadline"),
                buffer,
                requests);
    }

    private ToolExecution energySet(JsonNode args) {
        EnergyLevel level = requiredEnergy(args, "level");
        var response = energy.setToday(level);
        return ToolExecution.ok("Énergie déclarée : " + label(level) + ".", response);
    }

    private ToolExecution nowGet() {
        NowResponse response = now.getNow();
        String summary = response.task() != null
                ? "Action proposée : « " + response.task().title() + " »."
                : response.message();
        return ToolExecution.ok(summary, response);
    }

    private ToolExecution journalList() {
        List<JournalResponse> entries = journal.listRecent();
        return ToolExecution.ok(entries.size() + " action(s) récente(s).", entries);
    }

    private ToolExecution journalUndo(JsonNode args) {
        Long actionId = args != null && args.hasNonNull("action_id")
                ? args.get("action_id").asLong()
                : null;
        if (actionId == null) {
            actionId = journal.listRecent().stream()
                    .filter(JournalResponse::undoable)
                    .map(JournalResponse::id)
                    .findFirst()
                    .orElseThrow(() -> new ToolInputException("Aucune action annulable."));
        }
        List<JournalResponse> entries = journal.undo(actionId);
        return ToolExecution.ok("Dernière action annulée.", entries);
    }

    private EventKind requiredKind(JsonNode args, String field) {
        try {
            return EventKind.valueOf(requiredText(args, field));
        } catch (IllegalArgumentException exception) {
            throw new ToolInputException("Le type d'événement est invalide.");
        }
    }

    private TaskPriority requiredPriority(JsonNode args, String field) {
        try {
            return TaskPriority.valueOf(requiredText(args, field));
        } catch (IllegalArgumentException exception) {
            throw new ToolInputException("La priorité est invalide.");
        }
    }

    private EnergyLevel requiredEnergy(JsonNode args, String field) {
        try {
            return EnergyLevel.valueOf(requiredText(args, field));
        } catch (IllegalArgumentException exception) {
            throw new ToolInputException("Le niveau d'énergie est invalide.");
        }
    }

    private LocalDate requiredDate(JsonNode args, String field) {
        try {
            return LocalDate.parse(requiredText(args, field));
        } catch (RuntimeException exception) {
            throw new ToolInputException("La date " + field + " est invalide.");
        }
    }

    private LocalDate optionalDate(JsonNode args, String field) {
        if (args == null || !args.hasNonNull(field) || args.get(field).asText().isBlank()) {
            return null;
        }
        return requiredDate(args, field);
    }

    private Instant requiredInstant(JsonNode args, String field) {
        try {
            return Instant.parse(requiredText(args, field));
        } catch (RuntimeException exception) {
            throw new ToolInputException("L'instant " + field + " est invalide.");
        }
    }

    private Instant optionalInstant(JsonNode args, String field, Instant fallback) {
        if (args == null || !args.hasNonNull(field) || args.get(field).asText().isBlank()) {
            return fallback;
        }
        return requiredInstant(args, field);
    }

    private long requiredLong(JsonNode args, String field) {
        if (args == null || !args.hasNonNull(field) || !args.get(field).canConvertToLong()) {
            throw new ToolInputException("L'identifiant " + field + " est obligatoire.");
        }
        return args.get(field).asLong();
    }

    private int requiredInt(JsonNode args, String field) {
        if (args == null || !args.hasNonNull(field) || !args.get(field).canConvertToInt()) {
            throw new ToolInputException("Le nombre " + field + " est obligatoire.");
        }
        return args.get(field).asInt();
    }

    private String requiredText(JsonNode args, String field) {
        if (args == null || !args.hasNonNull(field) || args.get(field).asText().isBlank()) {
            throw new ToolInputException("Le champ " + field + " est obligatoire.");
        }
        return args.get(field).asText().trim();
    }

    private String optionalText(JsonNode args, String field, String fallback) {
        if (args == null || !args.hasNonNull(field) || args.get(field).asText().isBlank()) {
            return fallback;
        }
        return args.get(field).asText().trim();
    }

    private String label(EnergyLevel level) {
        return switch (level) {
            case LOW -> "basse";
            case MID -> "moyenne";
            case GOOD -> "bonne";
        };
    }
}
