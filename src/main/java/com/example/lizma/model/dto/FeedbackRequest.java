package com.example.lizma.model.dto;

import com.example.lizma.model.enums.FeedbackCategory;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A message from the feedback form. {@code contactEmail} is optional: the user leaves it to get an answer.
 * {@code appVersion} and {@code osVersion} are filled in by the app and help with problem reports.
 */
public record FeedbackRequest(
        @NotNull(message = "Choose what your message is about")
        FeedbackCategory category,
        @NotBlank(message = "Write a message")
        @Size(max = 2000, message = "The message can be at most 2000 characters long")
        String message,
        @Email(message = "This doesn't look like an email address")
        @Size(max = 254, message = "This doesn't look like an email address")
        String contactEmail,
        @Size(max = 32) String appVersion,
        @Size(max = 32) String osVersion,
        /** Screenshots uploaded before (POST /user/attachments), at most three. */
        @Size(max = 3, message = "You can add up to 3 screenshots") List<String> attachmentIds) {
}
