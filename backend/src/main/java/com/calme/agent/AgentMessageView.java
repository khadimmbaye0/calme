package com.calme.agent;

import java.time.Instant;

public record AgentMessageView(
        Long id,
        AgentMessageRole role,
        String content,
        Instant createdAt,
        boolean hasImage) {

    public static AgentMessageView from(AgentMessage message, boolean hasImage) {
        return new AgentMessageView(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt(),
                hasImage);
    }
}
