package com.java.springtotp.service;

import com.java.springtotp.dto.email.ResendEmailRequest;

public interface EmailService {

    void sendEmail(ResendEmailRequest request);
    void senTotpEnabledAlert(String to, String username);

}
