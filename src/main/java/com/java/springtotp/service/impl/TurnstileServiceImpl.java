package com.java.springtotp.service.impl;

import com.java.springtotp.properties.TurnstileProperties;
import com.java.springtotp.service.TurnstileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@RequiredArgsConstructor
@Service
public class TurnstileServiceImpl implements TurnstileService {

    private final RestClient restClient;
    private final TurnstileProperties turnstileProperties;

    @Override
    public boolean verifyToken(String captchaToken, String remoteIp) {
        if (!turnstileProperties.enabled()) {
            log.debug("");
            return true;
        }
        if (captchaToken == null || captchaToken.isBlank()) {
            return false;
        }

    }

    @Override
    public void validateOrThrow(String captchaToken, String remoteIp) {

    }
}
