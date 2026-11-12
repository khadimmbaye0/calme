package com.calme.tasks;

public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException() {
        super("Cette tâche n'existe pas.");
    }
}
