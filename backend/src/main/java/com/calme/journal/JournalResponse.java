package com.calme.journal;

import java.time.Instant;

public record JournalResponse(
        Long id,
        String intent,
        String explanation,
        AgentActionStatus status,
        Instant createdAt,
        boolean undoable) {
}
