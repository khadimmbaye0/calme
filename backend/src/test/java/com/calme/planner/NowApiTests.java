package com.calme.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.calme.TestAuth;
import com.calme.tasks.TaskPriority;
import com.calme.tasks.TaskRequest;
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
@Import(NowApiTests.FixedClock.class)
class NowApiTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);

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
        jdbcTemplate.update("delete from event");
        jdbcTemplate.update("delete from task");
        jdbcTemplate.update("delete from energy_log");
    }

    @Test
    void showsARestMessageWhenNothingIsDue() throws Exception {
        mockMvc.perform(get("/api/now"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task").value(nullValue()))
                .andExpect(jsonPath("$.energy").value("GOOD"))
                .andExpect(jsonPath("$.blockType").value("SCHOOL"))
                .andExpect(jsonPath("$.message", containsString("Rien à faire")));
    }

    @Test
    void selectsTheCriticalShortTaskFirst() throws Exception {
        createTask("Normale", 15, TaskPriority.NORMAL);
        createTask("Critique", 10, TaskPriority.CRITICAL);

        mockMvc.perform(get("/api/now"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.title").value("Critique"))
                .andExpect(jsonPath("$.task.estimateMax").value(10))
                .andExpect(jsonPath("$.task.priority").value("CRITICAL"));
    }

    @Test
    void startsAndCompletesATask() throws Exception {
        long taskId = createTask("Relire le plan", 15, TaskPriority.NORMAL);

        mockMvc.perform(post("/api/now/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":" + taskId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DOING"));

        mockMvc.perform(post("/api/now/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":" + taskId + "}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/tasks/{id}/done", taskId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void laterAndTooHeavyDeferTheTaskAndReturnTheNextAction() throws Exception {
        long laterId = createTask("Plus tard", 15, TaskPriority.NORMAL);
        mockMvc.perform(post("/api/now/later")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":" + laterId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task").value(nullValue()))
                .andExpect(jsonPath("$.message", containsString("reporté")));

        long heavyId = createTask("Trop lourd", 15, TaskPriority.NORMAL);
        mockMvc.perform(post("/api/now/too-heavy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":" + heavyId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task").value(nullValue()))
                .andExpect(jsonPath("$.message", containsString("Rien d'autre")));
    }

    @Test
    void lowEnergyDefersNonCriticalTasksButKeepsCriticalOnes() throws Exception {
        long normalId = createTask("Normale", 15, TaskPriority.NORMAL);
        createTask("Critique", 10, TaskPriority.CRITICAL);

        mockMvc.perform(put("/api/energy/today")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"level\":\"LOW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("LOW"))
                .andExpect(jsonPath("$.deferredCount").value(1));

        mockMvc.perform(get("/api/now"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.energy").value("LOW"))
                .andExpect(jsonPath("$.task.title").value("Critique"));

        String dueDate = jdbcTemplate.queryForObject(
                "select due_date::text from task where id = ?", String.class, normalId);
        assertThat(dueDate).isEqualTo("2026-11-13");
    }

    @Test
    void fixedEventCoveringTheWindowLeavesNoSlot() throws Exception {
        createTask("Relire le plan", 15, TaskPriority.NORMAL);
        Long userId = jdbcTemplate.queryForObject(
                "select id from app_user where lower(email) = lower('local@calme.invalid')",
                Long.class);
        jdbcTemplate.update("""
                insert into event (user_id, title, start_at, end_at, kind, created_by)
                values (?, 'Cours', ?, ?, 'FIXED', 'USER')
                """,
                userId,
                OffsetDateTime.parse("2026-11-12T17:30:00Z"),
                OffsetDateTime.parse("2026-11-12T22:00:00Z"));

        mockMvc.perform(get("/api/now"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task").value(nullValue()))
                .andExpect(jsonPath("$.message", containsString("Rien ne rentre")));
    }

    @Test
    void plannedEventCoveringTheWindowAlsoLeavesNoSlot() throws Exception {
        createTask("Relire le plan", 15, TaskPriority.NORMAL);
        Long userId = jdbcTemplate.queryForObject(
                "select id from app_user where lower(email) = lower('local@calme.invalid')",
                Long.class);
        jdbcTemplate.update("""
                insert into event (user_id, title, start_at, end_at, kind, created_by)
                values (?, 'Rédaction planifiée', ?, ?, 'PLANNED', 'USER')
                """,
                userId,
                OffsetDateTime.parse("2026-11-12T17:30:00Z"),
                OffsetDateTime.parse("2026-11-12T22:00:00Z"));

        mockMvc.perform(get("/api/now"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task").value(nullValue()))
                .andExpect(jsonPath("$.message", containsString("Rien ne rentre")));
    }

    @Test
    void rejectsInvalidTaskInput() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"estimateMin\":10,\"estimateMax\":10,\"priority\":\"NORMAL\"}"))
                .andExpect(status().isBadRequest());
    }

    private long createTask(String title, int estimate, TaskPriority priority) throws Exception {
        String body = objectMapper.writeValueAsString(
                new TaskRequest(title, estimate, estimate, priority, TODAY));
        String response = mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }
}
