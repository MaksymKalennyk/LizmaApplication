package com.example.lizma.model.dto;

/** Error body returned by every failing endpoint. */
public record ApiError(String message) {
}
