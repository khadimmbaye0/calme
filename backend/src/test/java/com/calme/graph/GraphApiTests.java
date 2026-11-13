package com.calme.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

import com.calme.TestAuth;
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
@Import(GraphApiTests.FixedClock.class)
class GraphApiTests {

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
        jdbcTemplate.update("delete from energy_log");
        jdbcTemplate.update("delete from agent_action");
        jdbcTemplate.update("delete from message");
    }

    @Test
    void connectsProjectsTasksEventsJournalEnergyBlocksAndMessages() throws Exception {
        String projectResponse = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Mémoire",
                                  "goal": "Finir le chapitre 2",
                                  "deadline": "2026-11-13",
                                  "bufferRatio": 0.2,
                                  "steps": [
                                    {"title": "Cadrage", "estimateMin": 30, "estimateMax": 30},
                                    {"title": "Rédaction", "estimateMin": 30, "estimateMax": 30, "dependsOn": [0]}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long projectId = objectMapper.readTree(projectResponse).get("id").asLong();
        long firstTaskId = objectMapper.readTree(projectResponse)
                .get("steps").get(0).get("taskId").asLong();

        mockMvc.perform(put("/api/energy/today")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"level\":\"MID\"}"))
                .andExpect(status().isOk());

        jdbcTemplate.update(
                "insert into message (user_id, role, content, created_at) values ("
                        + "(select id from app_user where email = 'local@calme.invalid'), 'USER', ?, ?)",
                "Je veux avancer le mémoire.",
                java.sql.Timestamp.from(Instant.parse("2026-11-12T10:00:00Z")));
        jdbcTemplate.update(
                "insert into message (user_id, role, content, created_at) values ("
                        + "(select id from app_user where email = 'local@calme.invalid'), 'AGENT', ?, ?)",
                "Je te propose une étape à 17h30.",
                java.sql.Timestamp.from(Instant.parse("2026-11-12T10:00:05Z")));

        String response = mockMvc.perform(get("/api/graph"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode graph = objectMapper.readTree(response);

        Set<String> nodeTypes = new HashSet<>();
        Set<String> projectIds = new HashSet<>();
        for (JsonNode node : graph.get("nodes")) {
            nodeTypes.add(node.get("type").asText());
            if ("PROJECT".equals(node.get("type").asText())) {
                projectIds.add(node.get("id").asText());
            }
        }
        assertThat(nodeTypes).contains(
                "PROJECT", "TASK", "EVENT", "ACTION", "ENERGY", "BLOCK", "MESSAGE");
        assertThat(projectIds).contains("project:" + projectId);

        Set<String> edgeTypes = new HashSet<>();
        boolean projectAgit = false;
        boolean energyAgit = false;
        boolean taskDepends = false;
        for (JsonNode edge : graph.get("edges")) {
            String type = edge.get("type").asText();
            edgeTypes.add(type);
            if ("DEPENDS_ON".equals(type)) {
                taskDepends = true;
            }
            if ("ACTED_ON".equals(type)
                    && edge.get("target").asText().equals("project:" + projectId)) {
                projectAgit = true;
            }
            if ("ACTED_ON".equals(type) && edge.get("target").asText().startsWith("energy:")) {
                energyAgit = true;
            }
        }
        assertThat(edgeTypes).contains(
                "CONTAINS", "DEPENDS_ON", "SCHEDULED_FOR", "ACTED_ON", "COVERS", "FOLLOWS");
        assertThat(taskDepends).isTrue();
        assertThat(projectAgit).isTrue();
        assertThat(energyAgit).isTrue();
        assertThat(graph.get("nodes").size()).isGreaterThan(10);
        assertThat(graph.get("edges").size()).isGreaterThan(10);

        boolean taskInGraph = false;
        for (JsonNode node : graph.get("nodes")) {
            if (node.get("id").asText().equals("task:" + firstTaskId)) {
                taskInGraph = true;
                assertThat(node.get("label").asText()).isEqualTo("Cadrage");
            }
        }
        assertThat(taskInGraph).isTrue();
    }

    @Test
    void exposesAnEmptyGraphWithoutWriting() throws Exception {
        String response = mockMvc.perform(get("/api/graph"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode graph = objectMapper.readTree(response);

        assertThat(graph.get("nodes").isArray()).isTrue();
        assertThat(graph.get("edges").isArray()).isTrue();
        assertThat(graph.get("nodes").size()).isEqualTo(14);
        assertThat(graph.get("edges").size()).isZero();
    }
}
