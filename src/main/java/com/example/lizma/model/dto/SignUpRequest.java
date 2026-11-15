package com.example.lizma.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Registration request")
public class SignUpRequest {

    @Schema(description = "Username: 5-50 characters, letters, digits, '.', '_' or '-'", example = "jon_doe")
    @NotBlank(message = "Username cannot be blank")
    @Size(min = 5, max = 50, message = "Username must be between 5 and 50 characters")
    @Pattern(regexp = "^[A-Za-z0-9_.-]*$", message = "Username can contain only letters, digits, '.', '_' and '-'")
    private String username;

    @Schema(description = "Password", example = "my_1secret1_password")
    @NotBlank(message = "Password cannot be blank")
    @Size(min = 8, max = 255, message = "Password length must be between 8 and 255 characters")
    private String password;

    @Schema(description = "Must be true: the user accepted the Terms of Use and Privacy Policy", example = "true")
    @AssertTrue(message = "You need to accept the Terms of Use and Privacy Policy")
    private boolean acceptTerms;
}
