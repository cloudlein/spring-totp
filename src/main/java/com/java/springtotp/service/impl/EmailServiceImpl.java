package com.java.springtotp.service.impl;

import com.java.springtotp.dto.email.ResendEmailRequest;
import com.java.springtotp.dto.email.ResendEmailResponse;
import com.java.springtotp.properties.ResendProperties;
import com.java.springtotp.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Slf4j
@RequiredArgsConstructor
@Service
public class EmailServiceImpl implements EmailService {

    private final ResendProperties resendProperties;
    private final RestClient restClient;
    private final TemplateEngine templateEngine;

    @Override
    public void sendEmail(ResendEmailRequest request) {
        ResendEmailResponse responseSent =  restClient.post()
                .uri("/emails")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ResendEmailResponse.class);

        log.debug("Resend Response : {}", responseSent);
    }

    @Override
    public void senTotpEnabledAlert(String to, String username) {
        try {
            Context context = new Context();
            context.setVariable("to", to);
            context.setVariable("username", username);
            context.setVariable("enabledAt", ZonedDateTime.now(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm 'UTC'", Locale.ENGLISH)));
            context.setVariable("supportEmail", "support@dev.com");

            String html = templateEngine.process("totp-enabled",context );

            ResendEmailRequest request = ResendEmailRequest
                    .builder()
                    .from("App <noreply@dev.com>")
                    .to(List.of(to))
                    .subject("Two-Factor Authentication Has Been Enabled on Your Account")
                    .htmlContent(html)
                    .build();

            sendEmail(request);

        }catch (Exception e) {
            log.error("Error enabled TOTP 2FA");
        }
    }
}
