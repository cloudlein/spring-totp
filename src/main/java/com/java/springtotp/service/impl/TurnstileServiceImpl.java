package com.java.springtotp.service.impl;

import com.java.springtotp.service.TurnstileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
@Service
public class TurnstileServiceImpl implements TurnstileService {

    private final RestClient restClient;

    @Override
    public boolean verifyToken(String captchaToken, String remoteIp) {
        return false;
    }

    @Override
    public void validateOrThrow(String captchaToken, String remoteIp) {

    }
}
