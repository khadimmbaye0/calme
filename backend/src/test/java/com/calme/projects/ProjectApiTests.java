package com.calme.projects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import com.calme.TestAuth;
import com.calme.tasks.TaskPriority;
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
@Import(ProjectApiTests.FixedClock.class)
class ProjectApiTests {

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
        jdbcTemplate.update("delete from task_dependency");
        jdbcTemplate.update("delete from event");
        jdbcTemplate.update("delete from task");
        jdbcTemplate.update("delete from project");
    }

    @Test
    void createsPlanWithDependenciesAndScheduledEvents() throws Exception {
        JsonNode created = createProject(
                "Mémoire",
                "Planifier le mémoire",
                TOMORROW,
                "0.20",
                List.of(
                        step("Cadrage", 30),
                        step("Rédaction", 30, 0)));

        assertThat(created.get("totalSteps").asInt()).isEqualTo(2);
        assertThat(created.get("doneSteps").asInt()).isZero();

        JsonNode first = created.get("steps").get(0);
        JsonNode second = created.get("steps").get(1);
        long firstTaskId = first.get("taskId").asLong();
        assertThat(second.get("dependsOnTaskIds").size()).isEqualTo(1);
        assertThat(second.get("dependsOnTaskIds").get(0).asLong()).isEqualTo(firstTaskId);
        assertThat(Instant.parse(first.get("scheduledStart").asText()))
                .isBefore(Instant.parse(first.get("scheduledEnd").asText()));
        assertThat(Instant.parse(second.get("scheduledStart").asText()))
                .isAfterOrEqualTo(Instant.parse(first.get("scheduledEnd").asText()));

        Integer eventCount = jdbcTemplate.queryForObject(
                "select count(*) from event where task_id is not null and kind = 'PLANNED'",
                Integer.class);
        assertThat(eventCount).isEqualTo(2);

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].totalSteps").value(2))
                .andExpect(jsonPath("$[0].doneSteps").value(0));

        mockMvc.perform(post("/api/tasks/{id}/done", firstTaskId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projects/{id}", created.get("id").asLong()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doneSteps").value(1))
                .andExpect(jsonPath("$.progress").value(0.5));
    }

    @Test
    void rejectsCyclicDependenciesWithoutWriting() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson(
                                "Cycle",
                                "Objectif",
                                TOMORROW,
                                "0.20",
                                List.of(
                                        step("A", 30, 1),
                                        step("B", 30, 0)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requête invalide"));

        assertThat(projectCount()).isZero();
    }

    @Test
    void rejectsBufferBelowTwentyPercent() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson(
                                "Marge",
                                "Objectif",
                                TOMORROW,
                                "0.10",
                                List.of(step("A", 30)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La marge du projet doit être d'au moins 20 %."));

        assertThat(projectCount()).isZero();
    }

    @Test
    void rejectsProjectThatDoesNotFitWithoutWriting() throws Exception {
        List<ProjectStepRequest> tooManySteps = java.util.stream.IntStream.range(0, 10)
                .mapToObj(index -> step("Étape " + index, 25))
                .toList();

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson(
                                "Trop long",
                                "Objectif",
                                TODAY,
                                "0.20",
                                tooManySteps)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Projet non planifiable"))
                .andExpect(jsonPath("$.detail", containsString("Réduis le périmètre")));
    }

    @Test
    void rejectsPastDeadlineAndUnknownProject() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson(
                                "Passé",
                                "Objectif",
                                TODAY.minusDays(1),
                                "0.20",
                                List.of(step("A", 30)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("L'échéance doit être aujourd'hui ou plus tard."));

        mockMvc.perform(get("/api/projects/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Projet introuvable"));
    }

    private JsonNode createProject(
            String title,
            String goal,
            LocalDate deadline,
            String buffer,
            List<ProjectStepRequest> steps) throws Exception {
        String response = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson(title, goal, deadline, buffer, steps)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private String projectJson(
            String title,
            String goal,
            LocalDate deadline,
            String buffer,
            List<ProjectStepRequest> steps) throws Exception {
        return objectMapper.writeValueAsString(new ProjectPlanRequest(
                title,
                goal,
                deadline,
                new BigDecimal(buffer),
                steps));
    }

    private ProjectStepRequest step(String title, int estimate, int... dependencies) {
        return new ProjectStepRequest(
                title,
                estimate,
                estimate,
                TaskPriority.NORMAL,
                java.util.Arrays.stream(dependencies).boxed().toList());
    }

    private int projectCount() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from project", Integer.class);
        return count == null ? 0 : count;
    }
}
