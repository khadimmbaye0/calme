package com.calme.graph;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.calme.agent.AgentMessage;
import com.calme.agent.AgentMessageRepository;
import com.calme.agent.AgentMessageRole;
import com.calme.blocks.Block;
import com.calme.blocks.BlockRepository;
import com.calme.blocks.BlockType;
import com.calme.calendar.Event;
import com.calme.calendar.EventRepository;
import com.calme.energy.EnergyLevel;
import com.calme.energy.EnergyLog;
import com.calme.energy.EnergyLogRepository;
import com.calme.journal.AgentAction;
import com.calme.journal.AgentActionRepository;
import com.calme.projects.Project;
import com.calme.projects.ProjectRepository;
import com.calme.projects.TaskDependency;
import com.calme.projects.TaskDependencyRepository;
import com.calme.tasks.Task;
import com.calme.tasks.TaskRepository;
import com.calme.tasks.TaskStatus;
import com.calme.user.AppUser;
import com.calme.user.CurrentUserService;
import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GraphService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final TaskDependencyRepository dependencies;
    private final EventRepository events;
    private final EnergyLogRepository energyLogs;
    private final BlockRepository blocks;
    private final AgentActionRepository actions;
    private final AgentMessageRepository messages;
    private final CurrentUserService currentUser;

    public GraphService(
            ProjectRepository projects,
            TaskRepository tasks,
            TaskDependencyRepository dependencies,
            EventRepository events,
            EnergyLogRepository energyLogs,
            BlockRepository blocks,
            AgentActionRepository actions,
            AgentMessageRepository messages,
            CurrentUserService currentUser) {
        this.projects = projects;
        this.tasks = tasks;
        this.dependencies = dependencies;
        this.events = events;
        this.energyLogs = energyLogs;
        this.blocks = blocks;
        this.actions = actions;
        this.messages = messages;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public GraphResponse graph() {
        AppUser user = currentUser.requireCurrentUser();
        ZoneId zone = ZoneId.of(user.getTimezone());

        List<Project> projectRows = projects.findByUserIdOrderByDeadlineAsc(user.getId());
        List<Task> taskRows = tasks.findTop150ByUserIdOrderByIdDesc(user.getId());
        List<Event> eventRows = events.findTop80ByUserIdOrderByStartAtDesc(user.getId());
        List<EnergyLog> energyRows = energyLogs.findTop30ByUserIdOrderByEnergyDateDescIdDesc(user.getId());
        List<AgentAction> actionRows = actions.findTop30ByUserIdOrderByCreatedAtDescIdDesc(user.getId());
        List<AgentMessage> messageRows = messages.findTop30ByUserIdOrderByCreatedAtDescIdDesc(user.getId())
                .stream()
                .limit(20)
                .toList();
        List<Block> blockRows = blocks.findByUserIdOrderByStartDateAsc(user.getId());

        Map<String, GraphNodeResponse> nodes = new LinkedHashMap<>();
        List<GraphEdgeResponse> edges = new ArrayList<>();
        Set<String> edgeKeys = new LinkedHashSet<>();

        Map<Long, Task> taskById = new HashMap<>();
        for (Task task : taskRows) {
            taskById.put(task.getId(), task);
        }
        Map<Long, Long> taskCounts = new HashMap<>();
        Map<Long, Long> doneCounts = new HashMap<>();
        for (Task task : taskRows) {
            if (task.getProjectId() != null) {
                taskCounts.merge(task.getProjectId(), 1L, Long::sum);
                if (task.getStatus() == TaskStatus.DONE) {
                    doneCounts.merge(task.getProjectId(), 1L, Long::sum);
                }
            }
        }

        for (Project project : projectRows) {
            long total = taskCounts.getOrDefault(project.getId(), 0L);
            long done = doneCounts.getOrDefault(project.getId(), 0L);
            nodes.put(projectNode(project.getId()), new GraphNodeResponse(
                    projectNode(project.getId()),
                    "PROJECT",
                    project.getTitle(),
                    DAY.format(project.getDeadline()) + " · " + done + "/" + total + " étapes",
                    project.getStatus(),
                    3.4));
        }

        for (Task task : taskRows) {
            String due = task.getDueDate() == null ? "sans date" : DAY.format(task.getDueDate());
            nodes.put(taskNode(task.getId()), new GraphNodeResponse(
                    taskNode(task.getId()),
                    "TASK",
                    task.getTitle(),
                    task.getEstimateMin() + "–" + task.getEstimateMax() + " min · " + due,
                    task.getStatus().name(),
                    2.0));
        }

        for (Event event : eventRows) {
            var start = event.getStartAt().atZone(zone);
            nodes.put(eventNode(event.getId()), new GraphNodeResponse(
                    eventNode(event.getId()),
                    "EVENT",
                    event.getTitle(),
                    DAY.format(start.toLocalDate()) + " · " + TIME.format(start)
                            + "–" + TIME.format(event.getEndAt().atZone(zone)),
                    event.getKind().name(),
                    2.0));
        }

        Map<LocalDate, String> energyNodeByDate = new HashMap<>();
        String latestEnergyId = energyRows.isEmpty() ? null : energyNode(energyRows.get(0).getId());
        for (EnergyLog log : energyRows) {
            String nodeId = energyNode(log.getId());
            nodes.put(nodeId, new GraphNodeResponse(
                    nodeId,
                    "ENERGY",
                    energyLabel(log.getLevel()),
                    DAY.format(log.getEnergyDate()),
                    log.getLevel().name(),
                    1.0));
            energyNodeByDate.putIfAbsent(log.getEnergyDate(), nodeId);
        }

        for (Block block : blockRows) {
            String label = block.getType() == BlockType.SCHOOL ? "Bloc école" : "Bloc entreprise";
            nodes.put(blockNode(block.getId()), new GraphNodeResponse(
                    blockNode(block.getId()),
                    "BLOCK",
                    label,
                    DAY.format(block.getStartDate()) + " → " + DAY.format(block.getEndDate()),
                    block.getType().name(),
                    3.0));
        }

        for (AgentAction action : actionRows) {
            String nodeId = actionNode(action.getId());
            nodes.put(nodeId, new GraphNodeResponse(
                    nodeId,
                    "ACTION",
                    truncate(action.getExplanation(), 82),
                    action.getIntent() + " · " + DAY.format(action.getCreatedAt().atZone(zone).toLocalDate()),
                    action.getStatus().name(),
                    1.2));
        }

        for (AgentMessage message : messageRows) {
            String nodeId = messageNode(message.getId());
            nodes.put(nodeId, new GraphNodeResponse(
                    nodeId,
                    "MESSAGE",
                    truncate(message.getContent(), 72),
                    (message.getRole() == AgentMessageRole.AGENT ? "Agent" : "Vous")
                            + " · " + DAY.format(message.getCreatedAt().atZone(zone).toLocalDate()),
                    message.getRole().name(),
                    0.9));
        }

        for (Task task : taskRows) {
            if (task.getProjectId() != null) {
                addEdge(nodes, edges, edgeKeys, projectNode(task.getProjectId()), taskNode(task.getId()),
                        "CONTAINS", "contient");
            }
        }
        if (!taskById.isEmpty()) {
            for (TaskDependency dependency : dependencies.findByTaskIdIn(taskById.keySet())) {
                addEdge(nodes, edges, edgeKeys,
                        taskNode(dependency.getId().getTaskId()),
                        taskNode(dependency.getId().getDependsOnTaskId()),
                        "DEPENDS_ON", "dépend de");
            }
        }
        for (Event event : eventRows) {
            if (event.getTaskId() != null) {
                addEdge(nodes, edges, edgeKeys, eventNode(event.getId()), taskNode(event.getTaskId()),
                        "SCHEDULED_FOR", "planifie");
            }
        }

        for (AgentAction action : actionRows) {
            addActionEdges(action, zone, energyNodeByDate, latestEnergyId, nodes, edges, edgeKeys);
        }

        for (Block block : blockRows) {
            for (Project project : projectRows) {
                if (within(project.getDeadline(), block)) {
                    addEdge(nodes, edges, edgeKeys, blockNode(block.getId()), projectNode(project.getId()),
                            "COVERS", "couvre");
                }
            }
            for (Task task : taskRows) {
                if (task.getDueDate() != null && within(task.getDueDate(), block)) {
                    addEdge(nodes, edges, edgeKeys, blockNode(block.getId()), taskNode(task.getId()),
                            "COVERS", "couvre");
                }
            }
            for (Event event : eventRows) {
                LocalDate eventDate = event.getStartAt().atZone(zone).toLocalDate();
                if (within(eventDate, block)) {
                    addEdge(nodes, edges, edgeKeys, blockNode(block.getId()), eventNode(event.getId()),
                            "COVERS", "couvre");
                }
            }
        }

        List<AgentMessage> chronological = messageRows.stream()
                .sorted(Comparator.comparing(AgentMessage::getCreatedAt).thenComparing(AgentMessage::getId))
                .toList();
        for (int index = 1; index < chronological.size(); index++) {
            addEdge(nodes, edges, edgeKeys,
                    messageNode(chronological.get(index - 1).getId()),
                    messageNode(chronological.get(index).getId()),
                    "FOLLOWS", "suite");
        }

        return new GraphResponse(List.copyOf(nodes.values()), List.copyOf(edges));
    }

    private void addActionEdges(
            AgentAction action,
            ZoneId zone,
            Map<LocalDate, String> energyNodeByDate,
            String latestEnergyId,
            Map<String, GraphNodeResponse> nodes,
            List<GraphEdgeResponse> edges,
            Set<String> edgeKeys) {
        String actionId = actionNode(action.getId());
        JsonNode payload = action.getPayload();
        String intent = action.getIntent();

        switch (intent) {
            case "event.create", "event.update", "event.delete" -> {
                Long eventId = longField(payload, "eventId");
                if (eventId != null) {
                    addEdge(nodes, edges, edgeKeys, actionId, eventNode(eventId), "ACTED_ON", "agit sur");
                }
            }
            case "task.create", "task.start", "task.done", "task.defer" -> {
                Long taskId = longField(payload, "taskId");
                if (taskId != null) {
                    addEdge(nodes, edges, edgeKeys, actionId, taskNode(taskId), "ACTED_ON", "agit sur");
                }
            }
            case "project.plan" -> {
                Long projectId = longField(payload, "projectId");
                if (projectId != null) {
                    addEdge(nodes, edges, edgeKeys, actionId, projectNode(projectId), "ACTED_ON", "agit sur");
                }
            }
            case "energy.set" -> {
                LocalDate actionDate = action.getCreatedAt().atZone(zone).toLocalDate();
                String energyId = energyNodeByDate.get(actionDate);
                if (energyId == null) {
                    energyId = latestEnergyId;
                }
                if (energyId == null) {
                    return;
                }
                addEdge(nodes, edges, edgeKeys, actionId, energyId, "ACTED_ON", "déclare");
                JsonNode deferred = action.getInversePayload() == null
                        ? null
                        : action.getInversePayload().get("deferredTasks");
                if (deferred != null && deferred.isArray()) {
                    for (JsonNode item : deferred) {
                        Long taskId = longField(item, "taskId");
                        if (taskId != null) {
                            addEdge(nodes, edges, edgeKeys, energyId, taskNode(taskId), "DEFERRED", "a reporté");
                        }
                    }
                }
            }
            default -> {
                // An action without a known target stays visible as a journal node.
            }
        }
    }

    private static boolean within(LocalDate date, Block block) {
        return !date.isBefore(block.getStartDate()) && !date.isAfter(block.getEndDate());
    }

    private static void addEdge(
            Map<String, GraphNodeResponse> nodes,
            List<GraphEdgeResponse> edges,
            Set<String> edgeKeys,
            String source,
            String target,
            String type,
            String label) {
        if (!nodes.containsKey(source) || !nodes.containsKey(target) || source.equals(target)) {
            return;
        }
        String key = source + "|" + target + "|" + type;
        if (edgeKeys.add(key)) {
            edges.add(new GraphEdgeResponse(key, source, target, type, label));
        }
    }

    private static Long longField(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asLong() : null;
    }

    private static String energyLabel(EnergyLevel level) {
        return switch (level) {
            case LOW -> "Énergie basse";
            case MID -> "Énergie moyenne";
            case GOOD -> "Énergie bonne";
        };
    }

    private static String truncate(String value, int max) {
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() <= max ? compact : compact.substring(0, max - 1).trim() + "…";
    }

    private static String projectNode(long id) {
        return "project:" + id;
    }

    private static String taskNode(long id) {
        return "task:" + id;
    }

    private static String eventNode(long id) {
        return "event:" + id;
    }

    private static String energyNode(long id) {
        return "energy:" + id;
    }

    private static String blockNode(long id) {
        return "block:" + id;
    }

    private static String actionNode(long id) {
        return "action:" + id;
    }

    private static String messageNode(long id) {
        return "message:" + id;
    }
}
