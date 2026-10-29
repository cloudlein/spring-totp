package com.java.springtotp.dto.role;

import lombok.*;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RoleResponse {

    private Long id;
    private String role;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
