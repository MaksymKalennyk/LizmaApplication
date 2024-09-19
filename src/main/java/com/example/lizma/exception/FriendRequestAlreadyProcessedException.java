package com.example.lizma.exception;

public class FriendRequestAlreadyProcessedException extends RuntimeException {
    public FriendRequestAlreadyProcessedException(String message) {
        super(message);
    }
}
