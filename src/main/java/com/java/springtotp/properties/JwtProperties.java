package com.java.springtotp.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "application.security.jwt")
public record JwtProperties(
        String secretKey,
        long expiration,
        RefreshToken refreshToken,
        long mfaExpiration
) {
    public record RefreshToken(long expiration) {}
}