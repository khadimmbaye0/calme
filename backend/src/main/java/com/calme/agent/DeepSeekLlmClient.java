package com.calme.agent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
@ConditionalOnProperty(name = "calme.llm.mode", havingValue = "deepseek")
public class DeepSeekLlmClient implements LlmClient {

    private final RestClient client;
    private final String apiKey;
    private final String model;

    public DeepSeekLlmClient(
            @Value("${calme.llm.deepseek.api-key:}") String apiKey,
            @Value("${calme.llm.deepseek.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${calme.llm.deepseek.model:deepseek-flash}") String model) {
        this.apiKey = apiKey;
        this.model = model;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(90));

        String normalizedBaseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        this.client = RestClient.builder()
                .baseUrl(normalizedBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public LlmResponse chat(List<LlmMessage> messages, List<Map<String, Object>> tools) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new LlmException("La clé DeepSeek n'est pas configurée.");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", messages.stream().map(this::toPayload).toList());
        payload.put("tools", tools);
        payload.put("tool_choice", "auto");
        payload.put("temperature", 0.2);

        try {
            JsonNode response = client.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !response.path("choices").isArray() || response.path("choices").isEmpty()) {
                throw new LlmException("L'agent n'a pas renvoyé de réponse exploitable.");
            }

            JsonNode message = response.path("choices").get(0).path("message");
            String content = message.hasNonNull("content") ? message.get("content").asText() : "";
            List<LlmToolCall> toolCalls = new ArrayList<>();
            JsonNode calls = message.path("tool_calls");
            if (calls.isArray()) {
                for (JsonNode call : calls) {
                    JsonNode function = call.path("function");
                    toolCalls.add(new LlmToolCall(
                            call.path("id").asText(),
                            function.path("name").asText(),
                            function.path("arguments").asText("{}")));
                }
            }

            if (content.isBlank() && toolCalls.isEmpty()) {
                throw new LlmException("L'agent n'a pas renvoyé d'action.");
            }
            return new LlmResponse(content, toolCalls);
        } catch (LlmException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw new LlmException("L'agent est momentanément indisponible.");
        } catch (ResourceAccessException exception) {
            throw new LlmException("L'agent met trop de temps à répondre. Réessaie dans un instant.");
        } catch (RuntimeException exception) {
            throw new LlmException("L'agent est momentanément indisponible.");
        }
    }

    private Map<String, Object> toPayload(LlmMessage message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("role", message.role());

        if (message.toolCallId() != null) {
            payload.put("tool_call_id", message.toolCallId());
            payload.put("content", message.content() == null ? "" : message.content());
            return payload;
        }

        if (message.imageDataUrl() != null && "user".equals(message.role())) {
            List<Map<String, Object>> content = new ArrayList<>();
            if (message.content() != null && !message.content().isBlank()) {
                content.add(Map.of("type", "text", "text", message.content()));
            }
            content.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", message.imageDataUrl(), "detail", "auto")));
            payload.put("content", content);
        } else {
            payload.put("content", message.content() == null ? "" : message.content());
        }
        if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
            payload.put("tool_calls", message.toolCalls().stream()
                    .map(call -> Map.of(
                            "id", call.id(),
                            "type", "function",
                            "function", Map.of(
                                    "name", call.name(),
                                    "arguments", call.arguments())))
                    .toList());
        }
        return payload;
    }
}
