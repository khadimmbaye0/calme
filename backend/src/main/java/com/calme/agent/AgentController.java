package com.calme.agent;

import java.time.Duration;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agent;

    public AgentController(AgentService agent) {
        this.agent = agent;
    }

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AgentChatResponse chat(@Valid @RequestBody AgentChatRequest request) {
        return agent.chat(request.message());
    }

    @PostMapping(value = "/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AgentChatResponse chatWithImage(
            @RequestParam("message") String message,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return agent.chat(message, AgentImageInput.from(image));
    }

    @GetMapping("/messages/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable long id) {
        AgentImage image = agent.image(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.mediaType()))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"calme-image." + image.extension() + "\"")
                .body(image.bytes());
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
