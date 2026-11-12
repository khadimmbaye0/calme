package com.calme.web;

import com.calme.calendar.EventConflictException;
import com.calme.calendar.EventNotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(EventNotFoundException.class)
    ProblemDetail handleNotFound(EventNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Événement introuvable", exception.getMessage());
    }

    @ExceptionHandler(EventConflictException.class)
    ProblemDetail handleConflict(EventConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Créneau indisponible", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("La requête est invalide.");
        return problem(HttpStatus.BAD_REQUEST, "Requête invalide", detail);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Requête invalide", exception.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableMessage(HttpMessageNotReadableException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Requête invalide", "Le contenu de la requête est invalide.");
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    ProblemDetail handleBadParameter(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "Requête invalide", "Les paramètres de date sont invalides.");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
