package com.calme.graph;

import java.util.List;

public record GraphResponse(
        List<GraphNodeResponse> nodes,
        List<GraphEdgeResponse> edges) {
}
