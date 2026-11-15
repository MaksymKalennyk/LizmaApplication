package com.example.lizma.exception;

public class FriendRequestAlreadyExistsException extends ConflictException {
    public FriendRequestAlreadyExistsException(String message) {
        super(message);
    }
}
