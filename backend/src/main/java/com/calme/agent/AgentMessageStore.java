package com.calme.agent;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentMessageStore {

    private static final Duration IMAGE_RETENTION = Duration.ofDays(30);

    private final AgentMessageRepository messages;
    private final AgentMessageImageRepository images;

    public AgentMessageStore(AgentMessageRepository messages, AgentMessageImageRepository images) {
        this.messages = messages;
        this.images = images;
    }

    @Transactional
    public AgentMessage saveUserMessage(AgentMessage message, AgentImageInput image) {
        AgentMessage saved = messages.save(message);
        if (image != null) {
            images.save(new AgentMessageImage(saved, image.mediaType(), image.bytes()));
            images.deleteOlderThan(Instant.now().minus(IMAGE_RETENTION));
        }
        return saved;
    }
}
