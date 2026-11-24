package com.example.lizma.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request token")
public class JwtAuthenticationResponse {
    @Schema(description = "Access token (JWT) for the Authorization header", example = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJhZG1pbiIsImV4cCI6MTYyMjUwNj...")
    private String token;

    @Schema(description = "Trade it at /auth/refresh for a new pair before or after the access token expires; single use")
    private String refreshToken;

    @Schema(description = "Seconds until the access token expires", example = "900")
    private long expiresIn;
}
