package com.calme.journal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import com.calme.TestAuth;
import com.calme.calendar.EventKind;
import com.calme.calendar.EventRequest;
import com.calme.projects.ProjectPlanRequest;
import com.calme.projects.ProjectStepRequest;
import com.calme.tasks.TaskPriority;
import com.calme.tasks.TaskRequest;
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
@Import(JournalApiTests.FixedClock.class)
class JournalApiTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);
    private static final LocalDate TOMORROW = LocalDate.of(2026, 11, 13);

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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("delete from agent_action");
        jdbcTemplate.update("delete from task_dependency");
        jdbcTemplate.update("delete from event");
        jdbcTemplate.update("delete from task");
        jdbcTemplate.update("delete from project");
        jdbcTemplate.update("delete from energy_log");
    }

    @Test
    void undoesEventCreate() throws Exception {
        long eventId = createEvent("Cours", "2027-01-10T09:00:00Z", "2027-01-10T10:00:00Z");
        JsonNode journal = journal();
        assertThat(journal.size()).isEqualTo(1);
        assertThat(journal.get(0).get("intent").asText()).isEqualTo("event.create");
        assertThat(journal.get(0).get("undoable").asBoolean()).isTrue();

        undo(journal.get(0).get("id").asLong())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("UNDONE"))
                .andExpect(jsonPath("$[0].undoable").value(false));

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from event where id = ?", Integer.class, eventId)).isZero();
    }

    @Test
    void undoesEventUpdate() throws Exception {
        long eventId = createEvent("Cours", "2027-01-10T09:00:00Z", "2027-01-10T10:00:00Z");
        mockMvc.perform(put("/api/events/{id}", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EventRequest(
                                "Cours décalé",
                                Instant.parse("2027-01-10T11:00:00Z"),
                                Instant.parse("2027-01-10T12:00:00Z"),
                                EventKind.PLANNED))))
                .andExpect(status().isOk());

        JsonNode journal = journal();
        assertThat(journal.size()).isEqualTo(2);
        undo(journal.get(0).get("id").asLong()).andExpect(status().isOk());

        JsonNode event = objectMapper.readTree(mockMvc.perform(get("/api/events/{id}", eventId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(event.get("title").asText()).isEqualTo("Cours");
        assertThat(event.get("startAt").asText()).isEqualTo("2027-01-10T09:00:00Z");
    }

    @Test
    void undoesEventDeleteWithTheSameId() throws Exception {
        long eventId = createEvent("Cours", "2027-01-10T09:00:00Z", "2027-01-10T10:00:00Z");
        mockMvc.perform(delete("/api/events/{id}", eventId)).andExpect(status().isNoContent());

        JsonNode journal = journal();
        assertThat(journal.size()).isEqualTo(2);
        undo(journal.get(0).get("id").asLong()).andExpect(status().isOk());

        JsonNode event = objectMapper.readTree(mockMvc.perform(get("/api/events/{id}", eventId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(event.get("id").asLong()).isEqualTo(eventId);
        assertThat(event.get("title").asText()).isEqualTo("Cours");
    }

    @Test
    void undoesTaskStartAndDone() throws Exception {
        long taskId = createTask("Relire le plan");
        mockMvc.perform(post("/api/now/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":" + taskId + "}"))
                .andExpect(status().isOk());

        JsonNode journal = journal();
        undo(journal.get(0).get("id").asLong()).andExpect(status().isOk());
        assertThat(taskStatus(taskId)).isEqualTo("TODO");

        mockMvc.perform(post("/api/tasks/{id}/done", taskId)).andExpect(status().isNoContent());
        JsonNode doneJournal = journal();
        undo(doneJournal.get(0).get("id").asLong()).andExpect(status().isOk());
        assertThat(taskStatus(taskId)).isEqualTo("TODO");
    }

    @Test
    void undoesProjectPlan() throws Exception {
        JsonNode project = objectMapper.readTree(mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProjectPlanRequest(
                                "Mémoire",
                                "Finir le chapitre 2",
                                TOMORROW,
                                new BigDecimal("0.20"),
                                List.of(
                                        new ProjectStepRequest("Cadrage", 30, 30, TaskPriority.NORMAL, List.of()),
                                        new ProjectStepRequest("Rédaction", 30, 30, TaskPriority.NORMAL, List.of(0)))))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());

        JsonNode journal = journal();
        assertThat(journal.get(0).get("intent").asText()).isEqualTo("project.plan");
        undo(journal.get(0).get("id").asLong()).andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("select count(*) from project", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from task", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from event", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from task_dependency", Integer.class)).isZero();
        assertThat(project.get("id").asLong()).isPositive();
    }

    @Test
    void undoesLowEnergyDeferralAndRestoresThePreviousLevel() throws Exception {
        createTask("Normale", TaskPriority.NORMAL);
        createTask("Critique", TaskPriority.CRITICAL);

        mockMvc.perform(put("/api/energy/today")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"level\":\"LOW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deferredCount").value(1));

        JsonNode journal = journal();
        assertThat(journal.get(0).get("intent").asText()).isEqualTo("energy.set");
        undo(journal.get(0).get("id").asLong()).andExpect(status().isOk());

        mockMvc.perform(get("/api/energy/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("GOOD"));

        List<String> dueDates = jdbcTemplate.queryForList(
                "select due_date::text from task order by id", String.class);
        assertThat(dueDates).containsExactly("2026-11-12", "2026-11-12");
    }

    @Test
    void onlyTheLatestAppliedActionCanBeUndone() throws Exception {
        createEvent("Un", "2027-01-10T09:00:00Z", "2027-01-10T10:00:00Z");
        createEvent("Deux", "2027-01-10T11:00:00Z", "2027-01-10T12:00:00Z");

        JsonNode journal = journal();
        assertThat(journal.size()).isEqualTo(2);
        long latestId = journal.get(0).get("id").asLong();
        long olderId = journal.get(1).get("id").asLong();

        undo(olderId).andExpect(status().isConflict());
        undo(latestId).andExpect(status().isOk());
        undo(olderId).andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("select count(*) from event", Integer.class)).isZero();
    }

    private long createEvent(String title, String startAt, String endAt) throws Exception {
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

    private long createTask(String title) throws Exception {
        return createTask(title, TaskPriority.NORMAL);
    }

    private long createTask(String title, TaskPriority priority) throws Exception {
        String response = mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TaskRequest(title, 15, 15, priority, TODAY))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private JsonNode journal() throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/journal"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private org.springframework.test.web.servlet.ResultActions undo(long id) throws Exception {
        return mockMvc.perform(post("/api/journal/{id}/undo", id));
    }

    private String taskStatus(long taskId) {
        return jdbcTemplate.queryForObject(
                "select status from task where id = ?", String.class, taskId);
    }
}
