package com.calme.calendar;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "local@calme.invalid", roles = "USER")
class CalendarApiTests {

    private static final String DAY_FROM = "2027-01-10T00:00:00Z";
    private static final String DAY_TO = "2027-01-11T00:00:00Z";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void authPassword(DynamicPropertyRegistry registry) {
        registry.add("calme.auth.password", () -> com.calme.TestAuth.PASSWORD);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanEvents() {
        jdbcTemplate.update("delete from event");
    }

    @Test
    void supportsTheManualCalendarLifecycle() throws Exception {
        String response = create(
                "Cours",
                "2027-01-10T09:00:00Z",
                "2027-01-10T10:00:00Z",
                EventKind.FIXED)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Cours"))
                .andExpect(jsonPath("$.kind").value("FIXED"))
                .andExpect(jsonPath("$.createdBy").value("USER"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long id = objectMapper.readTree(response).get("id").asLong();

        listDay()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(put("/api/events/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(
                                "Cours décalé",
                                "2027-01-10T11:00:00Z",
                                "2027-01-10T12:00:00Z",
                                EventKind.PLANNED)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Cours décalé"))
                .andExpect(jsonPath("$.kind").value("PLANNED"));

        mockMvc.perform(delete("/api/events/{id}", id))
                .andExpect(status().isNoContent());

        listDay()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void listsOnlyEventsOverlappingTheRequestedRange() throws Exception {
        create("Jour", "2027-01-10T09:00:00Z", "2027-01-10T10:00:00Z", EventKind.FIXED)
                .andExpect(status().isCreated());
        create("Lendemain", "2027-01-11T09:00:00Z", "2027-01-11T10:00:00Z", EventKind.FIXED)
                .andExpect(status().isCreated());

        listDay()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Jour"));
    }

    @Test
    void rejectsOverlappingEventsWithoutWriting() throws Exception {
        create("Cours", "2027-01-10T09:00:00Z", "2027-01-10T10:00:00Z", EventKind.FIXED)
                .andExpect(status().isCreated());

        create("Réunion", "2027-01-10T09:30:00Z", "2027-01-10T10:30:00Z", EventKind.PLANNED)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Créneau indisponible"))
                .andExpect(jsonPath("$.detail").value("Ce créneau chevauche « Cours »."));

        listDay()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void allowsAvailabilityToOverlapAPlannedEvent() throws Exception {
        create("Cours", "2027-01-10T09:00:00Z", "2027-01-10T10:00:00Z", EventKind.FIXED)
                .andExpect(status().isCreated());
        create("Disponible", "2027-01-10T09:00:00Z", "2027-01-10T11:00:00Z", EventKind.AVAILABILITY)
                .andExpect(status().isCreated());

        listDay()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void rejectsInvalidRangesAndBlankTitles() throws Exception {
        create("Invalide", "2027-01-10T10:00:00Z", "2027-01-10T09:00:00Z", EventKind.FIXED)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requête invalide"))
                .andExpect(jsonPath("$.detail").value("La fin doit être après le début."));

        create("   ", "2027-01-10T09:00:00Z", "2027-01-10T10:00:00Z", EventKind.FIXED)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Le titre est obligatoire."));
    }

    @Test
    void rejectsUpdatesThatWouldCreateAConflict() throws Exception {
        long firstId = createAndGetId("Cours", "2027-01-10T09:00:00Z", "2027-01-10T10:00:00Z", EventKind.FIXED);
        create("Réunion", "2027-01-10T11:00:00Z", "2027-01-10T12:00:00Z", EventKind.FIXED)
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/events/{id}", firstId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(
                                "Cours",
                                "2027-01-10T11:30:00Z",
                                "2027-01-10T12:30:00Z",
                                EventKind.FIXED)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ce créneau chevauche « Réunion »."));

        listDay()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void returnsNotFoundAndRejectsInvalidQueryParameters() throws Exception {
        mockMvc.perform(get("/api/events/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Événement introuvable"));

        mockMvc.perform(get("/api/events")
                        .param("from", "pas-une-date")
                        .param("to", DAY_TO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requête invalide"));
    }

    private ResultActions create(String title, String startAt, String endAt, EventKind kind) throws Exception {
        return mockMvc.perform(post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(title, startAt, endAt, kind)));
    }

    private long createAndGetId(String title, String startAt, String endAt, EventKind kind) throws Exception {
        String response = create(title, startAt, endAt, kind)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private ResultActions listDay() throws Exception {
        return mockMvc.perform(get("/api/events")
                .param("from", DAY_FROM)
                .param("to", DAY_TO));
    }

    private String requestJson(String title, String startAt, String endAt, EventKind kind) throws Exception {
        return objectMapper.writeValueAsString(new EventRequest(
                title,
                Instant.parse(startAt),
                Instant.parse(endAt),
                kind));
    }
}
