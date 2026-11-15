package com.example.lizma.exception;

import org.springframework.http.HttpStatus;

public class InvalidActionException extends ApiException {
    public InvalidActionException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
