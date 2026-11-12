package com.calme.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AuthApiTests {

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

    private String loginJson(String password) {
        return "{\"password\":\"" + password + "\"}";
    }

    @Test
    void protectsCalendarWithoutSession() throws Exception {
        mockMvc.perform(get("/api/events")
                        .param("from", DAY_FROM)
                        .param("to", DAY_TO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logsInKeepsSessionAndLogsOut() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(com.calme.TestAuth.PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(LocalAccount.EMAIL))
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(LocalAccount.EMAIL));

        mockMvc.perform(get("/api/events")
                        .session(session)
                        .param("from", DAY_FROM)
                        .param("to", DAY_TO))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsBadPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("wrong")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Identifiants invalides"))
                .andExpect(jsonPath("$.detail").value("Le mot de passe ne correspond pas."));
    }

    @Test
    void rejectsBlankPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("")))
                .andExpect(status().isBadRequest());
    }
}
