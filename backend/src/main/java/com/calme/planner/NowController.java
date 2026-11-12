package com.calme.planner;

import jakarta.validation.Valid;

import com.calme.tasks.TaskResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/now")
public class NowController {

    private final NowService now;

    public NowController(NowService now) {
        this.now = now;
    }

    @GetMapping
    public NowResponse current() {
        return now.getNow();
    }

    @PostMapping("/start")
    public TaskResponse start(@Valid @RequestBody NowActionRequest request) {
        return now.start(request.taskId());
    }

    @PostMapping("/later")
    public NowResponse later(@Valid @RequestBody NowActionRequest request) {
        return now.later(request.taskId());
    }

    @PostMapping("/too-heavy")
    public NowResponse tooHeavy(@Valid @RequestBody NowActionRequest request) {
        return now.tooHeavy(request.taskId());
    }
}
