package com.java.springtotp.service.impl;

import com.java.springtotp.dto.captcha.TurnstileRequest;
import com.java.springtotp.dto.captcha.TurnstileResponse;
import com.java.springtotp.properties.TurnstileProperties;
import com.java.springtotp.service.TurnstileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@RequiredArgsConstructor
@Service
public class TurnstileServiceImpl implements TurnstileService {

    @Qualifier("turnstileClient")
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

        var request = TurnstileRequest.builder()
                .secret(turnstileProperties.secretKey())
                .response(captchaToken)
                .remoteIp(remoteIp)
                .build();

        try {
            TurnstileResponse response = restClient.post()
                    .uri(turnstileProperties.verifyUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(TurnstileResponse.class);

            if (response == null || !response.isSuccess()) {
                log.warn("Turnstile verification failed: {}",
                        response != null ? response.getErrorCodes() : "null response");
                return false;
            }

            return true;
        }catch (RestClientException e) {
            log.error("Turnstile verification error", e);
            return false;
        }


    }

    @Override
    public void validateOrThrow(String captchaToken, String remoteIp) {

    }
}
