package com.calme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CalmeApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void healthEndpointReportsUp() {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/api/health",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }

    @Test
    void initialMigrationCreatesTheCoreTables() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                  and table_name in (
                    'app_user', 'block', 'project', 'task', 'task_dependency',
                    'event', 'energy_log', 'agent_action', 'message', 'settings', 'digest_log'
                  )
                """, Integer.class);

        assertThat(tableCount).isEqualTo(11);
    }

    @Test
    void undecidedSettingsRemainNullByDefault() {
        Long userId = createUser("settings@example.invalid");
        jdbcTemplate.update("insert into settings (user_id) values (?)", userId);

        Map<String, Object> settings = jdbcTemplate.queryForMap(
                "select rest_start, rest_end, digest_time, capacity_ratio from settings where user_id = ?", userId);

        assertThat(settings.values()).allMatch(value -> value == null);
    }

    @Test
    void alternanceBlocksCannotOverlap() {
        Long userId = createUser("blocks@example.invalid");
        jdbcTemplate.update("""
                insert into block (user_id, type, start_date, end_date)
                values (?, 'SCHOOL', '2026-11-02', '2026-11-21')
                """, userId);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into block (user_id, type, start_date, end_date)
                values (?, 'COMPANY', '2026-11-20', '2026-12-06')
                """, userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Long createUser(String email) {
        return jdbcTemplate.queryForObject(
                "insert into app_user (email, timezone) values (?, 'UTC') returning id",
                Long.class, email);
    }
}
