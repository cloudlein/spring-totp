package com.java.springtotp.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RefreshTokenRequest {

    @NotBlank(message = "refresh token is required")
    @NotBlank(message = "refresh token is required")
    @Size(min = 10, max = 1024, message = "refresh token length is invalid")
    private String refreshToken;

}
