package com.java.springtotp.model;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "roles")
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class Role extends BaseModel {

    private String role;
}
