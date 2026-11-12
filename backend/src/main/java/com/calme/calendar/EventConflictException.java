package com.calme.calendar;

public class EventConflictException extends RuntimeException {

    public EventConflictException(String conflictingTitle) {
        super("Ce créneau chevauche « " + conflictingTitle + " ».");
    }
}
