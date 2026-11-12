package com.calme.auth;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Le mot de passe ne correspond pas.");
    }
}
