package com.calme.agent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.calme.blocks.BlockService;
import com.calme.blocks.BlockType;
import com.calme.energy.EnergyLevel;
import com.calme.energy.EnergyService;
import com.calme.journal.JournalResponse;
import com.calme.journal.JournalService;
import com.calme.user.AppUser;
import com.calme.user.CurrentUserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentService {

    private static final int MAX_TOOL_ROUNDS = 4;
    private static final int MAX_TOOL_CALLS = 14;
    private static final int HISTORY_MESSAGES = 20;
    private static final Duration CONFIRMATION_TTL = Duration.ofMinutes(10);
    private static final Set<String> WRITE_TOOLS = Set.of(
            "calendar_create", "calendar_update", "calendar_delete",
            "task_create", "task_start", "task_complete", "task_defer",
            "project_plan", "energy_set");

    private final CurrentUserService currentUser;
    private final AgentMessageRepository messages;
    private final AgentMessageImageRepository images;
    private final AgentMessageStore messageStore;
    private final LlmClient llm;
    private final AgentToolRegistry tools;
    private final AgentToolExecutor executor;
    private final JournalService journal;
    private final EnergyService energy;
    private final BlockService blocks;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<String, PendingAction> pendingActions = new ConcurrentHashMap<>();

    public AgentService(
            CurrentUserService currentUser,
            AgentMessageRepository messages,
            AgentMessageImageRepository images,
            AgentMessageStore messageStore,
            LlmClient llm,
            AgentToolRegistry tools,
            AgentToolExecutor executor,
            JournalService journal,
            EnergyService energy,
            BlockService blocks,
            ObjectMapper objectMapper,
            Clock clock) {
        this.currentUser = currentUser;
        this.messages = messages;
        this.images = images;
        this.messageStore = messageStore;
        this.llm = llm;
        this.tools = tools;
        this.executor = executor;
        this.journal = journal;
        this.energy = energy;
        this.blocks = blocks;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AgentMessageView> recentMessages() {
        AppUser user = currentUser.requireCurrentUser();
        List<AgentMessage> recent = new ArrayList<>(messages.findTop30ByUserIdOrderByCreatedAtDescIdDesc(user.getId()));
        Collections.reverse(recent);
        Set<Long> ids = recent.stream().map(AgentMessage::getId).collect(java.util.stream.Collectors.toSet());
        Set<Long> withImage = ids.isEmpty()
                ? Set.of()
                : new LinkedHashSet<>(images.findMessageIds(ids));
        return recent.stream()
                .map(message -> AgentMessageView.from(message, withImage.contains(message.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public AgentImage image(long id) {
        AppUser user = currentUser.requireCurrentUser();
        AgentMessage message = messages.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image introuvable."));
        AgentMessageImage image = images.findByMessage_Id(message.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image introuvable."));
        return new AgentImage(image.getMediaType(), image.getData());
    }

    public AgentChatResponse chat(String rawMessage) {
        return chat(rawMessage, null);
    }

    public synchronized AgentChatResponse chat(String rawMessage, AgentImageInput image) {
        AppUser user = currentUser.requireCurrentUser();
        String message = rawMessage == null ? "" : rawMessage.trim();
        if (message.isBlank() && image == null) {
            return response("Écris une phrase ou joins une image pour que je puisse t'aider.", List.of(), null, null, true);
        }
        if (message.length() > 2000) {
            throw new IllegalArgumentException("Le message doit faire 2000 caractères maximum.");
        }
        if (message.isBlank()) {
            message = "Regarde cette image.";
        }

        List<AgentMessage> history = new ArrayList<>(
                messages.findTop30ByUserIdOrderByCreatedAtDescIdDesc(user.getId()));
        Collections.reverse(history);
        if (history.size() > HISTORY_MESSAGES) {
            history = history.subList(history.size() - HISTORY_MESSAGES, history.size());
        }

        List<LlmMessage> conversation = new ArrayList<>();
        conversation.add(LlmMessage.system(systemPrompt(user)));
        for (AgentMessage previous : history) {
            if (previous.getRole() == AgentMessageRole.USER) {
                conversation.add(LlmMessage.user(previous.getContent()));
            } else {
                conversation.add(LlmMessage.assistant(previous.getContent(), List.of()));
            }
        }
        conversation.add(LlmMessage.user(message, image == null ? null : image.dataUrl()));
        messageStore.saveUserMessage(new AgentMessage(user, AgentMessageRole.USER, message), image);

        List<AgentActionView> actions = new ArrayList<>();
        boolean mutated = false;
        int toolCalls = 0;

        try {
            for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
                LlmResponse reply = llm.chat(conversation, tools.definitions());
                if (reply.toolCalls() == null || reply.toolCalls().isEmpty()) {
                    return finish(user, reply.content(), actions, mutated);
                }

                conversation.add(LlmMessage.assistant(reply.content(), reply.toolCalls()));
                for (LlmToolCall call : reply.toolCalls()) {
                    toolCalls++;
                    if (toolCalls > MAX_TOOL_CALLS) {
                        return finish(
                                user,
                                "Je m'arrête ici pour ne pas enchaîner trop d'actions. Reformule la demande en une étape.",
                                actions,
                                mutated,
                                true);
                    }

                    JsonNode args;
                    try {
                        args = parseArguments(call.arguments());
                    } catch (RuntimeException exception) {
                        conversation.add(LlmMessage.tool(call.id(), toolError("Les arguments de l'outil sont invalides.")));
                        continue;
                    }

                    if (image != null && WRITE_TOOLS.contains(call.name())) {
                        String id = UUID.randomUUID().toString();
                        String summary = "Action proposée depuis l'image. Vérifie-la puis confirme pour l'appliquer.";
                        pendingActions.put(id, new PendingAction(
                                id,
                                user.getId(),
                                call.name(),
                                args,
                                summary,
                                Instant.now()));
                        messages.save(new AgentMessage(user, AgentMessageRole.AGENT, summary));
                        return new AgentChatResponse(
                                summary,
                                actions,
                                new AgentPendingConfirmation(id, call.name(), summary),
                                mutated ? latestUndoableId() : null,
                                false);
                    }

                    ToolExecution execution;
                    try {
                        execution = executor.execute(call.name(), args);
                    } catch (RuntimeException exception) {
                        conversation.add(LlmMessage.tool(call.id(), toolError(calmError(exception))));
                        continue;
                    }

                    if (execution.confirmationRequired()) {
                        String id = UUID.randomUUID().toString();
                        pendingActions.put(id, new PendingAction(
                                id,
                                user.getId(),
                                call.name(),
                                args,
                                execution.summary(),
                                Instant.now()));
                        messages.save(new AgentMessage(user, AgentMessageRole.AGENT, execution.summary()));
                        return new AgentChatResponse(
                                execution.summary(),
                                actions,
                                new AgentPendingConfirmation(id, call.name(), execution.summary()),
                                mutated ? latestUndoableId() : null,
                                false);
                    }

                    if (!execution.ok()) {
                        conversation.add(LlmMessage.tool(call.id(), toolError(execution.summary())));
                        continue;
                    }

                    actions.add(new AgentActionView(call.name(), execution.summary()));
                    mutated = true;
                    conversation.add(LlmMessage.tool(call.id(), toolResult(execution)));
                }
            }
            return finish(
                    user,
                    "Je n'ai pas pu terminer cette demande. Réessaie avec une phrase plus simple.",
                    actions,
                    mutated,
                    true);
        } catch (LlmException exception) {
            return finish(user, exception.getMessage(), actions, mutated, true);
        } catch (RuntimeException exception) {
            return finish(user, "Je n'ai pas pu traiter cette demande. Réessaie dans un instant.", actions, mutated, true);
        }
    }

    public AgentChatResponse confirm(String id) {
        AppUser user = currentUser.requireCurrentUser();
        PendingAction pending = pendingActions.remove(id);
        if (pending == null || !pending.userId().equals(user.getId())) {
            throw new AgentConfirmationException("Cette confirmation n'existe plus.");
        }
        if (pending.createdAt().plus(CONFIRMATION_TTL).isBefore(Instant.now())) {
            throw new AgentConfirmationException("Cette confirmation a expiré. Redemande l'action.");
        }

        try {
            ToolExecution execution = executor.executeDirect(pending.tool(), pending.args());
            String message = execution.ok()
                    ? "C'est fait. " + execution.summary()
                    : execution.summary();
            messages.save(new AgentMessage(user, AgentMessageRole.AGENT, message));
            List<AgentActionView> actions = execution.ok()
                    ? List.of(new AgentActionView(pending.tool(), execution.summary()))
                    : List.of();
            return new AgentChatResponse(
                    message,
                    actions,
                    null,
                    execution.ok() ? latestUndoableId() : null,
                    !execution.ok());
        } catch (RuntimeException exception) {
            String message = calmError(exception);
            messages.save(new AgentMessage(user, AgentMessageRole.AGENT, message));
            return response(message, List.of(), null, null, true);
        }
    }

    public AgentChatResponse cancel(String id) {
        AppUser user = currentUser.requireCurrentUser();
        PendingAction pending = pendingActions.remove(id);
        if (pending == null || !pending.userId().equals(user.getId())) {
            throw new AgentConfirmationException("Cette confirmation n'existe plus.");
        }
        String message = "Action annulée. Rien n'a été modifié.";
        messages.save(new AgentMessage(user, AgentMessageRole.AGENT, message));
        return response(message, List.of(), null, null, false);
    }

    private AgentChatResponse finish(AppUser user, String content, List<AgentActionView> actions, boolean mutated) {
        return finish(user, content, actions, mutated, false);
    }

    private AgentChatResponse finish(
            AppUser user,
            String content,
            List<AgentActionView> actions,
            boolean mutated,
            boolean error) {
        String message = content == null || content.isBlank()
                ? "Je n'ai pas de réponse à te proposer pour l'instant."
                : content;
        messages.save(new AgentMessage(user, AgentMessageRole.AGENT, message));
        return response(message, actions, null, mutated ? latestUndoableId() : null, error);
    }

    private AgentChatResponse response(
            String message,
            List<AgentActionView> actions,
            AgentPendingConfirmation pending,
            Long undoableActionId,
            boolean error) {
        return new AgentChatResponse(message, actions, pending, undoableActionId, error);
    }

    private Long latestUndoableId() {
        try {
            return journal.listRecent().stream()
                    .filter(JournalResponse::undoable)
                    .map(JournalResponse::id)
                    .findFirst()
                    .orElse(null);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String systemPrompt(AppUser user) {
        ZoneId zone = ZoneId.of(user.getTimezone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(zone));
        EnergyLevel level = energy.currentToday().level();
        BlockType block = blocks.currentType(user, today).orElse(null);
        String blockLabel = block == null ? "aucun" : block == BlockType.SCHOOL ? "école" : "entreprise";

        return """
                Tu es Calme, l'agent d'organisation personnel de l'utilisateur.
                Ton ton est calme, court, sans reproche. Tu réponds en français.
                Date locale : %s. Heure locale : %s. Fuseau : %s.
                Bloc courant : %s. Énergie déclarée : %s.

                Tu disposes d'outils pour lire et modifier le calendrier, les tâches, les projets,
                l'énergie et le journal. Le backend valide et exécute chaque outil.
                Règles :
                - N'invente jamais d'identifiant, d'événement ou de tâche.
                - Utilise calendar_list, task_list ou project_list pour retrouver un élément avant
                  de le modifier ou de le supprimer.
                - Les heures sont en ISO-8601 UTC, les dates en YYYY-MM-DD.
                - Ne planifie pas sur un événement fixe ; le backend refuse les conflits.
                - Pour une suppression ou une action volumineuse, l'outil demandera une confirmation.
                  Ne prétends jamais avoir exécuté une action avant la confirmation.
                - Si une information manque, pose une seule question avec deux options maximum.
                - Après une action, explique-la en une phrase.
                - Ne demande jamais de mot de passe ni de donnée sensible.
                - Si un outil échoue, explique calmement et propose une alternative.
                - Si une image est jointe, lis-la comme un document ou un écran : repère les dates,
                  heures, titres et détails utiles, puis propose l'action d'organisation adaptée.
                  Ne recopie pas toute l'image ; résume ce que tu en retiens en une phrase.
                """.formatted(
                today,
                now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                zone,
                blockLabel,
                energyLabel(level));
    }

    private String energyLabel(EnergyLevel level) {
        return switch (level) {
            case LOW -> "basse";
            case MID -> "moyenne";
            case GOOD -> "bonne";
        };
    }

    private JsonNode parseArguments(String rawArguments) {
        if (rawArguments == null || rawArguments.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode parsed = objectMapper.readTree(rawArguments);
            if (parsed == null || !parsed.isObject()) {
                throw new ToolInputException("Les arguments de l'outil sont invalides.");
            }
            return parsed;
        } catch (JsonProcessingException exception) {
            throw new ToolInputException("Les arguments de l'outil sont invalides.");
        }
    }

    private String toolResult(ToolExecution execution) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("summary", execution.summary());
        if (execution.data() != null) {
            result.put("data", execution.data());
        }
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            return "{\"ok\":true,\"summary\":\"Action appliquée.\"}";
        }
    }

    private String toolError(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("ok", false, "error", message));
        } catch (JsonProcessingException exception) {
            return "{\"ok\":false,\"error\":\"Action impossible.\"}";
        }
    }

    private String calmError(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank() || message.length() > 240) {
            return "Je n'ai pas pu appliquer cette action.";
        }
        return message;
    }

    private record PendingAction(
            String id,
            Long userId,
            String tool,
            JsonNode args,
            String summary,
            Instant createdAt) {
    }
}
