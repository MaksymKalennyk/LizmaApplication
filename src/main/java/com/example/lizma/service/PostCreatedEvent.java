package com.example.lizma.service;

/** A post was saved; listeners run after the transaction committed. */
public record PostCreatedEvent(Long postId, Long authorId) {
}
