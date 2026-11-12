package com.calme.journal;

import com.calme.user.AppUser;
import com.calme.user.CurrentUserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JournalRecorder {

    private final AgentActionRepository actions;
    private final CurrentUserService currentUser;
    private final ObjectMapper objectMapper;

    public JournalRecorder(
            AgentActionRepository actions,
            CurrentUserService currentUser,
            ObjectMapper objectMapper) {
        this.actions = actions;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(String intent, Object payload, Object inversePayload, String explanation) {
        AppUser user = currentUser.requireCurrentUser();
        JsonNode payloadNode = payload == null
                ? objectMapper.createObjectNode()
                : objectMapper.valueToTree(payload);
        JsonNode inverseNode = inversePayload == null
                ? null
                : objectMapper.valueToTree(inversePayload);
        actions.save(new AgentAction(user, intent, payloadNode, inverseNode, explanation));
    }
}
