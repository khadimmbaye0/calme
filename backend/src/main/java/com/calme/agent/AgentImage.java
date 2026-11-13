package com.calme.agent;

public record AgentImage(String mediaType, byte[] bytes) {

    public String extension() {
        return switch (mediaType) {
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }
}
