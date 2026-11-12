package com.calme.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CalendarApiTests {

    private static final String DAY_FROM = "2027-01-10T00:00:00Z";
    private static final String DAY_TO = "2027-01-11T00:00:00Z";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanEvents() {
        jdbcTemplate.update("delete from event");
    }

    @Test
    void supportsTheManualCalendarLifecycle() {
        ResponseEntity<Map<String, Object>> created = create(
                "Cours",
                "2027-01-10T09:00:00Z",
                "2027-01-10T10:00:00Z",
                EventKind.FIXED);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody())
                .containsEntry("title", "Cours")
                .containsEntry("kind", "FIXED")
                .containsEntry("createdBy", "USER");
        Number id = (Number) created.getBody().get("id");
        assertThat(id).isNotNull();

        assertThat(listDay().getBody()).hasSize(1);

        ResponseEntity<Map<String, Object>> updated = restTemplate.exchange(
                "/api/events/" + id.longValue(),
                HttpMethod.PUT,
                new HttpEntity<>(new EventRequest(
                        "Cours décalé",
                        Instant.parse("2027-01-10T11:00:00Z"),
                        Instant.parse("2027-01-10T12:00:00Z"),
                        EventKind.PLANNED)),
                new ParameterizedTypeReference<>() {
                });

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody())
                .containsEntry("title", "Cours décalé")
                .containsEntry("kind", "PLANNED");

        ResponseEntity<Void> deleted = restTemplate.exchange(
                "/api/events/" + id.longValue(), HttpMethod.DELETE, null, Void.class);

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(listDay().getBody()).isEmpty();
    }

    @Test
    void listsOnlyEventsOverlappingTheRequestedRange() {
        assertThat(create(
                "Jour",
                "2027-01-10T09:00:00Z",
                "2027-01-10T10:00:00Z",
                EventKind.FIXED).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(create(
                "Lendemain",
                "2027-01-11T09:00:00Z",
                "2027-01-11T10:00:00Z",
                EventKind.FIXED).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(listDay().getBody())
                .extracting(event -> event.get("title"))
                .containsExactly("Jour");
    }

    @Test
    void rejectsOverlappingEventsWithoutWriting() {
        assertThat(create(
                "Cours",
                "2027-01-10T09:00:00Z",
                "2027-01-10T10:00:00Z",
                EventKind.FIXED).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map<String, Object>> conflict = create(
                "Réunion",
                "2027-01-10T09:30:00Z",
                "2027-01-10T10:30:00Z",
                EventKind.PLANNED);

        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody()).containsEntry("title", "Créneau indisponible");
        assertThat((String) conflict.getBody().get("detail")).contains("Cours");
        assertThat(listDay().getBody()).hasSize(1);
    }

    @Test
    void allowsAvailabilityToOverlapAPlannedEvent() {
        assertThat(create(
                "Cours",
                "2027-01-10T09:00:00Z",
                "2027-01-10T10:00:00Z",
                EventKind.FIXED).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(create(
                "Disponible",
                "2027-01-10T09:00:00Z",
                "2027-01-10T11:00:00Z",
                EventKind.AVAILABILITY).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(listDay().getBody()).hasSize(2);
    }

    @Test
    void rejectsInvalidRangesAndBlankTitles() {
        ResponseEntity<Map<String, Object>> invalidRange = create(
                "Invalide",
                "2027-01-10T10:00:00Z",
                "2027-01-10T09:00:00Z",
                EventKind.FIXED);

        assertThat(invalidRange.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalidRange.getBody()).containsEntry("title", "Requête invalide");
        assertThat((String) invalidRange.getBody().get("detail")).contains("après le début");

        ResponseEntity<Map<String, Object>> blankTitle = create(
                "   ",
                "2027-01-10T09:00:00Z",
                "2027-01-10T10:00:00Z",
                EventKind.FIXED);

        assertThat(blankTitle.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(blankTitle.getBody()).containsEntry("detail", "Le titre est obligatoire.");
    }

    @Test
    void rejectsUpdatesThatWouldCreateAConflict() {
        Number firstId = (Number) create(
                "Cours",
                "2027-01-10T09:00:00Z",
                "2027-01-10T10:00:00Z",
                EventKind.FIXED).getBody().get("id");
        assertThat(create(
                "Réunion",
                "2027-01-10T11:00:00Z",
                "2027-01-10T12:00:00Z",
                EventKind.FIXED).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map<String, Object>> conflict = restTemplate.exchange(
                "/api/events/" + firstId.longValue(),
                HttpMethod.PUT,
                new HttpEntity<>(new EventRequest(
                        "Cours",
                        Instant.parse("2027-01-10T11:30:00Z"),
                        Instant.parse("2027-01-10T12:30:00Z"),
                        EventKind.FIXED)),
                new ParameterizedTypeReference<>() {
                });

        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((String) conflict.getBody().get("detail")).contains("Réunion");
        assertThat(listDay().getBody()).hasSize(2);
    }

    @Test
    void returnsNotFoundAndRejectsInvalidQueryParameters() {
        ResponseEntity<Map<String, Object>> missing = restTemplate.exchange(
                "/api/events/999999",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {
                });

        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).containsEntry("title", "Événement introuvable");

        ResponseEntity<Map<String, Object>> invalidQuery = restTemplate.exchange(
                "/api/events?from=pas-une-date&to=" + DAY_TO,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {
                });

        assertThat(invalidQuery.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalidQuery.getBody()).containsEntry("title", "Requête invalide");
    }

    private ResponseEntity<Map<String, Object>> create(
            String title, String startAt, String endAt, EventKind kind) {
        return restTemplate.exchange(
                "/api/events",
                HttpMethod.POST,
                new HttpEntity<>(new EventRequest(title, Instant.parse(startAt), Instant.parse(endAt), kind)),
                new ParameterizedTypeReference<>() {
                });
    }

    private ResponseEntity<List<Map<String, Object>>> listDay() {
        return restTemplate.exchange(
                "/api/events?from=" + DAY_FROM + "&to=" + DAY_TO,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {
                });
    }
}
