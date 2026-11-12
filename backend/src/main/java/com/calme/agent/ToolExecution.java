package com.calme.agent;

public record ToolExecution(
        boolean ok,
        boolean confirmationRequired,
        String summary,
        Object data) {

    public static ToolExecution ok(String summary, Object data) {
        return new ToolExecution(true, false, summary, data);
    }

    public static ToolExecution confirm(String summary) {
        return new ToolExecution(false, true, summary, null);
    }

    public static ToolExecution error(String summary) {
        return new ToolExecution(false, false, summary, null);
    }
}
