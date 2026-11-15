package com.example.lizma.exception;

public class FriendRequestAlreadyProcessedException extends ConflictException {
    public FriendRequestAlreadyProcessedException(String message) {
        super(message);
    }
}
