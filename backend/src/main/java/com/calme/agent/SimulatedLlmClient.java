package com.calme.agent;

import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "calme.llm.mode", havingValue = "simulated", matchIfMissing = true)
public class SimulatedLlmClient implements LlmClient {

    @Override
    public LlmResponse chat(List<LlmMessage> messages, List<Map<String, Object>> tools) {
        return new LlmResponse(
                "Le mode simulé est actif. Configure CALME_LLM_MODE=deepseek et DEEPSEEK_API_KEY pour parler à l'agent réel.",
                List.of());
    }
}
