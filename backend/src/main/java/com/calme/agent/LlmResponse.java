package com.calme.agent;

import java.util.List;

public record LlmResponse(String content, List<LlmToolCall> toolCalls) {
}
