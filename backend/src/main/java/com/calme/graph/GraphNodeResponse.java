package com.calme.graph;

public record GraphNodeResponse(
        String id,
        String type,
        String label,
        String subtitle,
        String status,
        double weight) {
}
