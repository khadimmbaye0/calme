package com.calme.agent;

import java.util.List;

public record AgentChatResponse(
        String message,
        List<AgentActionView> actions,
        AgentPendingConfirmation pendingConfirmation,
        Long undoableActionId,
        boolean error) {
}
