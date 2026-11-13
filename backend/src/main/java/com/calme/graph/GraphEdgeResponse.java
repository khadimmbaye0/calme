package com.calme.graph;

public record GraphEdgeResponse(
        String id,
        String source,
        String target,
        String type,
        String label) {
}
