package com.java.springtotp.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "users")
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class User extends BaseModel {


    private String username;

    private String name;

    private String email;

    private String totpSecret;

    private Boolean isTotpEnabled;

    private String password;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id")
    private Role role;
}

