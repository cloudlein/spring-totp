package com.java.springtotp.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "application.turnstile")
public record TurnstileProperties(
        String secretKey,
        String siteKey,
        @DefaultValue("https://challenges.cloudflare.com/turnstile/v0/siteverify")
        String verifyUrl,
        @DefaultValue("true")
        boolean enabled
) {

}
