package com.calme.calendar;

public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException() {
        super("Cet événement n'existe pas.");
    }
}
