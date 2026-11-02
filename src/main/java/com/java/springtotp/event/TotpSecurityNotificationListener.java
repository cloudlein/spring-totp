package com.java.springtotp.event;

import com.java.springtotp.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class TotpSecurityNotificationListener {

    private final EmailService emailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTotpEnabled(TotpEnabledEvent event) {
        try {
            emailService.sendTotpEnabledAlert(event.email(), event.username());
        } catch (Exception e) {
            log.error("Failed to send TOTP enabled alert for user {}", event.username(), e);
        }
    }

}
