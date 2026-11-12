package com.calme.agent;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agent;

    public AgentController(AgentService agent) {
        this.agent = agent;
    }

    @PostMapping("/chat")
    public AgentChatResponse chat(@Valid @RequestBody AgentChatRequest request) {
        return agent.chat(request.message());
    }

    @GetMapping("/messages")
    public List<AgentMessageView> messages() {
        return agent.recentMessages();
    }

    @PostMapping("/confirm/{id}")
    public AgentChatResponse confirm(@PathVariable String id) {
        return agent.confirm(id);
    }

    @PostMapping("/cancel/{id}")
    public AgentChatResponse cancel(@PathVariable String id) {
        return agent.cancel(id);
    }
}
