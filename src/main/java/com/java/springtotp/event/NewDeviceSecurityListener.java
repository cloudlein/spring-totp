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
public class NewDeviceSecurityListener {

    private final EmailService emailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNewDeviceLogin(NewDeviceLoginEvent event) {
        try {
            emailService.sendNewDeviceLoginAlert(event.username(), event.email(), event.deviceMetadata());
        } catch (Exception e) {
            log.error("Failed to send new device login alert to {}: {}",
                    event.email(), e.getMessage(), e);
        }
    }

}
