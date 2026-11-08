package com.java.springtotp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Entity
@Table(name = "user_device",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_device_user_hash",
                columnNames = {"user_id", "device_hash"}))
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UserDevice extends BaseModel {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 255)
    private String deviceDetails;

    @Column(length = 64, nullable = false)
    private String deviceHash;

    @Column(length = 45)
    private String ipAddress;

    @Column(length = 255)
    private String location;

    @Column(length = 255)
    private String fingerPrint;

    private Instant lastLoginAt;
}