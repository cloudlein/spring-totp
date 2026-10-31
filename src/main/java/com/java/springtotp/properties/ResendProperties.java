package com.java.springtotp.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "application.resend")
public record ResendProperties(
        String apiKey,
        String fromEmail,
        String baseUrl
) {
}
