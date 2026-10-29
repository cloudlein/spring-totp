package com.java.springtotp.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserUpdateRequest {

    @Size(min = 4, max = 255, message = "Username must be between 4 and 255 characters")
    private String username;

    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @Email(message = "Invalid email format")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @Size(min = 8, max = 255, message = "Password must be between 8 and 255 characters")
    private String password;

    private Long roleId;

    private Boolean isTotpEnabled;
}
