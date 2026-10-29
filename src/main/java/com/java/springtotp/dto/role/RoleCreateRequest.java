package com.java.springtotp.dto.role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RoleCreateRequest {

    @NotBlank(message = "Role name is required")
    @Size(max = 255, message = "Role name must not exceed 255 characters")
    private String roleName;

}
