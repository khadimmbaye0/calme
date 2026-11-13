package com.calme.agent;

import java.util.List;

public record LlmMessage(
        String role,
        String content,
        List<LlmToolCall> toolCalls,
        String toolCallId,
        String imageDataUrl) {

    public static LlmMessage system(String content) {
        return new LlmMessage("system", content, List.of(), null, null);
    }

    public static LlmMessage user(String content) {
        return new LlmMessage("user", content, List.of(), null, null);
    }

    public static LlmMessage user(String content, String imageDataUrl) {
        return new LlmMessage("user", content, List.of(), null, imageDataUrl);
    }

    public static LlmMessage assistant(String content, List<LlmToolCall> toolCalls) {
        return new LlmMessage("assistant", content, toolCalls, null, null);
    }

    public static LlmMessage tool(String toolCallId, String content) {
        return new LlmMessage("tool", content, List.of(), toolCallId, null);
    }
}
