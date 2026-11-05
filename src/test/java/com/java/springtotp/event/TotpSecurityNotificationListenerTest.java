package com.java.springtotp.event;

import com.java.springtotp.service.EmailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TotpSecurityNotificationListenerTest {

    @Mock
    private EmailService emailService;

    @InjectMocks
    private TotpSecurityNotificationListener listener;

    @Test
    @DisplayName("onTotpEnabled delegates alert dispatch to emailService")
    void onTotpEnabled_ShouldInvokeEmailService() {
        TotpEnabledEvent event = new TotpEnabledEvent("user@example.com", "testuser", Instant.now());

        listener.onTotpEnabled(event);

        verify(emailService).sendTotpEnabledAlert("user@example.com", "testuser");
    }

    @Test
    @DisplayName("onTotpEnabled catches any exception thrown by emailService gracefully")
    void onTotpEnabled_WhenEmailServiceThrows_ShouldNotRethrow() {
        TotpEnabledEvent event = new TotpEnabledEvent("user@example.com", "testuser", Instant.now());
        doThrow(new RuntimeException("Network down")).when(emailService)
                .sendTotpEnabledAlert("user@example.com", "testuser");

        assertThatCode(() -> listener.onTotpEnabled(event))
                .doesNotThrowAnyException();
    }
}
