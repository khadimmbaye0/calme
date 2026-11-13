package com.calme.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.nullValue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;

import com.calme.TestAuth;
import com.calme.calendar.EventKind;
import com.calme.calendar.EventRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "local@calme.invalid", roles = "USER")
@Import({AgentApiTests.FixedClock.class, AgentApiTests.FakeLlm.class})
class AgentApiTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void authPassword(DynamicPropertyRegistry registry) {
        registry.add("calme.auth.password", () -> TestAuth.PASSWORD);
    }

    @TestConfiguration
    static class FixedClock {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-12T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    @TestConfiguration
    static class FakeLlm {

        @Bean
        @Primary
        FakeLlmClient fakeLlmClient() {
            return new FakeLlmClient();
        }
    }

    static class FakeLlmClient implements LlmClient {

        private final ConcurrentLinkedDeque<LlmResponse> responses = new ConcurrentLinkedDeque<>();
        private final AtomicReference<List<LlmMessage>> lastRequest = new AtomicReference<>(List.of());

        void enqueue(LlmResponse... values) {
            responses.addAll(List.of(values));
        }

        void clear() {
            responses.clear();
        }

        @Override
        public LlmResponse chat(List<LlmMessage> messages, List<Map<String, Object>> tools) {
            lastRequest.set(List.copyOf(messages));
            LlmResponse response = responses.poll();
            return response == null ? new LlmResponse("Je n'ai pas d'autre action.", List.of()) : response;
        }

        List<LlmMessage> lastRequest() {
            return lastRequest.get();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FakeLlmClient fakeLlm;

    @BeforeEach
    void clean() {
        fakeLlm.clear();
        jdbcTemplate.update("delete from message_image");
        jdbcTemplate.update("delete from message");
        jdbcTemplate.update("delete from agent_action");
        jdbcTemplate.update("delete from task_dependency");
        jdbcTemplate.update("delete from event");
        jdbcTemplate.update("delete from task");
        jdbcTemplate.update("delete from project");
        jdbcTemplate.update("delete from energy_log");
    }

    @Test
    void createsEventThroughToolAndJournalsIt() throws Exception {
        fakeLlm.enqueue(
                new LlmResponse("", List.of(new LlmToolCall(
                        "call_create",
                        "calendar_create",
                        objectMapper.writeValueAsString(Map.of(
                                "title", "Cours",
                                "start_at", "2027-01-10T09:00:00Z",
                                "end_at", "2027-01-10T10:00:00Z",
                                "kind", "FIXED"))))),
                new LlmResponse("J'ai ajouté le cours.", List.of()));

        JsonNode response = chat("Ajoute un cours le 10 janvier de 9h à 10h.");
        assertThat(response.get("message").asText()).isEqualTo("J'ai ajouté le cours.");
        assertThat(response.get("actions").size()).isEqualTo(1);
        assertThat(response.get("actions").get(0).get("tool").asText()).isEqualTo("calendar_create");
        assertThat(response.get("undoableActionId").isNumber()).isTrue();
        assertThat(jdbcTemplate.queryForObject("select count(*) from event", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from agent_action", Integer.class)).isEqualTo(1);

        long actionId = response.get("undoableActionId").asLong();
        mockMvc.perform(post("/api/journal/{id}/undo", actionId))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("select count(*) from event", Integer.class)).isZero();
    }

    @Test
    void answersNowWithoutJournal() throws Exception {
        fakeLlm.enqueue(
                new LlmResponse("", List.of(new LlmToolCall("call_now", "now_get", "{}"))),
                new LlmResponse("Rien à faire maintenant. Repose-toi.", List.of()));

        JsonNode response = chat("Qu'est-ce que je fais maintenant ?");
        assertThat(response.get("message").asText()).contains("Repose-toi");
        assertThat(response.get("undoableActionId").isNull()).isTrue();
        assertThat(jdbcTemplate.queryForObject("select count(*) from agent_action", Integer.class)).isZero();
    }

    @Test
    void invalidToolArgumentsWriteNothing() throws Exception {
        fakeLlm.enqueue(
                new LlmResponse("", List.of(new LlmToolCall(
                        "call_bad",
                        "calendar_create",
                        objectMapper.writeValueAsString(Map.of("title", "Incomplet"))))),
                new LlmResponse("Je n'ai pas pu créer cet événement.", List.of()));

        JsonNode response = chat("Crée un événement incomplet.");
        assertThat(response.get("message").asText()).contains("Je n'ai pas pu");
        assertThat(jdbcTemplate.queryForObject("select count(*) from event", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from agent_action", Integer.class)).isZero();
    }

    @Test
    void requiresConfirmationBeforeDeletingEvent() throws Exception {
        long eventId = createEventViaApi("Cours", "2027-01-10T09:00:00Z", "2027-01-10T10:00:00Z");
        fakeLlm.enqueue(new LlmResponse("", List.of(new LlmToolCall(
                "call_delete",
                "calendar_delete",
                objectMapper.writeValueAsString(Map.of("event_id", eventId))))));

        JsonNode response = chat("Supprime le cours du 10 janvier.");
        JsonNode pending = response.get("pendingConfirmation");
        assertThat(pending.isObject()).isTrue();
        assertThat(pending.get("tool").asText()).isEqualTo("calendar_delete");
        assertThat(jdbcTemplate.queryForObject("select count(*) from event", Integer.class)).isEqualTo(1);

        mockMvc.perform(post("/api/agent/confirm/{id}", pending.get("id").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingConfirmation").value(nullValue()))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("C'est fait")));

        assertThat(jdbcTemplate.queryForObject("select count(*) from event", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from agent_action", Integer.class)).isEqualTo(2);
    }

    @Test
    void cancelsPendingDeletion() throws Exception {
        long eventId = createEventViaApi("Cours", "2027-01-10T09:00:00Z", "2027-01-10T10:00:00Z");
        fakeLlm.enqueue(new LlmResponse("", List.of(new LlmToolCall(
                "call_delete",
                "calendar_delete",
                objectMapper.writeValueAsString(Map.of("event_id", eventId))))));

        JsonNode response = chat("Supprime le cours du 10 janvier.");
        String pendingId = response.get("pendingConfirmation").get("id").asText();

        mockMvc.perform(post("/api/agent/cancel/{id}", pendingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Action annulée. Rien n'a été modifié."));

        assertThat(jdbcTemplate.queryForObject("select count(*) from event", Integer.class)).isEqualTo(1);
    }

    @Test
    void exposesRecentMessages() throws Exception {
        fakeLlm.enqueue(new LlmResponse("Bonjour, comment puis-je t'aider ?", List.of()));
        chat("Bonjour");

        mockMvc.perform(get("/api/agent/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("USER"))
                .andExpect(jsonPath("$[0].content").value("Bonjour"))
                .andExpect(jsonPath("$[1].role").value("AGENT"))
                .andExpect(jsonPath("$[1].content").value("Bonjour, comment puis-je t'aider ?"));
    }

    @Test
    void asksConfirmationForLargeProjectPlan() throws Exception {
        String steps = objectMapper.writeValueAsString(List.of(
                Map.of("ref", "s1", "title", "Étape 1", "estimate_min", 15, "estimate_max", 15, "depends_on", List.of()),
                Map.of("ref", "s2", "title", "Étape 2", "estimate_min", 15, "estimate_max", 15, "depends_on", List.of("s1")),
                Map.of("ref", "s3", "title", "Étape 3", "estimate_min", 15, "estimate_max", 15, "depends_on", List.of("s2")),
                Map.of("ref", "s4", "title", "Étape 4", "estimate_min", 15, "estimate_max", 15, "depends_on", List.of("s3")),
                Map.of("ref", "s5", "title", "Étape 5", "estimate_min", 15, "estimate_max", 15, "depends_on", List.of("s4"))));
        Map<String, Object> args = Map.of(
                "title", "Mémoire",
                "goal", "Finir",
                "deadline", "2026-11-30",
                "buffer_ratio", 0.2,
                "steps", objectMapper.readTree(steps));
        fakeLlm.enqueue(new LlmResponse("", List.of(new LlmToolCall(
                "call_project",
                "project_plan",
                objectMapper.writeValueAsString(args)))));

        JsonNode response = chat("Planifie mon mémoire en cinq étapes.");
        assertThat(response.get("pendingConfirmation").isObject()).isTrue();
        assertThat(jdbcTemplate.queryForObject("select count(*) from project", Integer.class)).isZero();

        mockMvc.perform(post("/api/agent/cancel/{id}", response.get("pendingConfirmation").get("id").asText()))
                .andExpect(status().isOk());
    }

    @Test
    void acceptsImageSendsItToTheLlmAndStoresIt() throws Exception {
        fakeLlm.enqueue(new LlmResponse("J'ai lu l'image. Je te propose un créneau.", List.of()));
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        MockMultipartFile image = new MockMultipartFile("image", "plan.png", "image/png", png);

        mockMvc.perform(multipart("/api/agent/chat")
                        .file(image)
                        .param("message", "Ajoute ce cours.")
                        .param("imageConsent", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("J'ai lu l'image. Je te propose un créneau."));

        LlmMessage userMessage = fakeLlm.lastRequest().stream()
                .filter(message -> "user".equals(message.role()))
                .reduce((left, right) -> right)
                .orElseThrow();
        assertThat(userMessage.content()).isEqualTo("Ajoute ce cours.");
        assertThat(userMessage.imageDataUrl()).isEqualTo(
                "data:image/png;base64," + Base64.getEncoder().encodeToString(png));

        String messagesResponse = mockMvc.perform(get("/api/agent/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("USER"))
                .andExpect(jsonPath("$[0].hasImage").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode messages = objectMapper.readTree(messagesResponse);
        long messageId = messages.get(0).get("id").asLong();

        mockMvc.perform(get("/api/agent/messages/{id}/image", messageId))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType()).isEqualTo("image/png"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(png));
    }

    @Test
    void acceptsAnImageWithoutText() throws Exception {
        fakeLlm.enqueue(new LlmResponse("Je regarde l'image.", List.of()));
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "plan.png",
                "image/png",
                Base64.getDecoder().decode(
                        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="));

        mockMvc.perform(multipart("/api/agent/chat")
                        .file(image)
                        .param("message", "")
                        .param("imageConsent", "true"))
                .andExpect(status().isOk());

        String response = mockMvc.perform(get("/api/agent/messages"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode messages = objectMapper.readTree(response);
        assertThat(messages.get(0).get("content").asText()).isEqualTo("Regarde cette image.");
        assertThat(messages.get(0).get("hasImage").asBoolean()).isTrue();
    }

    @Test
    void rejectsNonImageUploadWithoutWriting() throws Exception {
        MockMultipartFile text = new MockMultipartFile(
                "image", "notes.txt", "text/plain", "pas une image".getBytes());

        mockMvc.perform(multipart("/api/agent/chat")
                        .file(text)
                        .param("message", "Lis ce fichier."))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Format d'image non pris en charge. Utilise JPEG, PNG, GIF ou WebP."));

        assertThat(jdbcTemplate.queryForObject("select count(*) from message", Integer.class)).isZero();
    }

    @Test
    void refusesAnImageWithoutExplicitConsent() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "plan.png",
                "image/png",
                Base64.getDecoder().decode(
                        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="));

        mockMvc.perform(multipart("/api/agent/chat")
                        .file(image)
                        .param("message", "Lis cette image."))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Confirme l'envoi de l'image à DeepSeek."));

        assertThat(jdbcTemplate.queryForObject("select count(*) from message", Integer.class)).isZero();
    }

    @Test
    void requiresConfirmationBeforeMutatingFromAnImage() throws Exception {
        fakeLlm.enqueue(
                new LlmResponse("", List.of(new LlmToolCall(
                        "call_create",
                        "calendar_create",
                        objectMapper.writeValueAsString(Map.of(
                                "title", "Cours lu sur l'image",
                                "start_at", "2027-01-10T09:00:00Z",
                                "end_at", "2027-01-10T10:00:00Z",
                                "kind", "FIXED"))))));
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "plan.png",
                "image/png",
                Base64.getDecoder().decode(
                        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="));

        JsonNode response = objectMapper.readTree(mockMvc.perform(multipart("/api/agent/chat")
                        .file(image)
                        .param("message", "Ajoute ce cours.")
                        .param("imageConsent", "true"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertThat(response.get("pendingConfirmation").isObject()).isTrue();
        assertThat(jdbcTemplate.queryForObject("select count(*) from event", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from agent_action", Integer.class)).isZero();
    }

    private JsonNode chat(String message) throws Exception {
        String response = mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AgentChatRequest(message))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private long createEventViaApi(String title, String startAt, String endAt) throws Exception {
        String response = mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EventRequest(
                                title,
                                Instant.parse(startAt),
                                Instant.parse(endAt),
                                EventKind.FIXED))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }
}
