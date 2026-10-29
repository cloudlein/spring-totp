package com.java.springtotp.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserResponse {

    private Long id;
    private Long roleId;
    private String username;
    private String name;
    private String email;
    private String password;
    private String role;
    private Boolean isTotpEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
