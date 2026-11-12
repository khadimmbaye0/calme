package com.calme.projects;

public class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException() {
        super("Ce projet n'existe pas.");
    }
}
