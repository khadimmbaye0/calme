package com.calme.journal;

public class JournalNotFoundException extends RuntimeException {

    public JournalNotFoundException() {
        super("Cette action n'existe pas.");
    }
}
