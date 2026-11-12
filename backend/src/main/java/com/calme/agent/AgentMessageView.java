package com.calme.agent;

import java.time.Instant;

public record AgentMessageView(
        Long id,
        AgentMessageRole role,
        String content,
        Instant createdAt) {

    public static AgentMessageView from(AgentMessage message) {
        return new AgentMessageView(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt());
    }
}
