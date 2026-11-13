package com.calme.graph;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/graph")
public class GraphController {

    private final GraphService graph;

    public GraphController(GraphService graph) {
        this.graph = graph;
    }

    @GetMapping
    public GraphResponse get() {
        return graph.graph();
    }
}
