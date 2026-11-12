package com.calme.agent;

import java.util.List;
import java.util.Map;

public interface LlmClient {

    LlmResponse chat(List<LlmMessage> messages, List<Map<String, Object>> tools);
}
