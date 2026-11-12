package com.calme.agent;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class AgentToolRegistry {

    private final List<Map<String, Object>> definitions;

    public AgentToolRegistry(ObjectMapper objectMapper) {
        try (InputStream input = new ClassPathResource("agent-tools.json").getInputStream()) {
            this.definitions = List.copyOf(objectMapper.readValue(
                    input,
                    new TypeReference<List<Map<String, Object>>>() {
                    }));
        } catch (IOException exception) {
            throw new IllegalStateException("Impossible de charger les outils de l'agent.", exception);
        }
    }

    public List<Map<String, Object>> definitions() {
        return definitions;
    }
}
