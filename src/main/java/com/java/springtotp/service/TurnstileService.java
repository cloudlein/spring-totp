package com.java.springtotp.service;

public interface TurnstileService {

    boolean verifyToken(String captchaToken, String remoteIp);

    void validateOrThrow(String captchaToken, String remoteIp);

}
