package com.example.lizma.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for errors that should reach the client as {@code {"message": "..."}}
 * with a specific HTTP status. See {@link GlobalExceptionHandler}.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
