package com.java.springtotp.event;

import com.java.springtotp.dto.userDevice.DeviceMetadata;
import com.java.springtotp.service.EmailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NewDeviceSecurityListenerTest {

    @Mock
    private EmailService emailService;

    @InjectMocks
    private NewDeviceSecurityListener listener;

    @Test
    @DisplayName("onNewDeviceLogin delegates alert dispatch to emailService")
    void onNewDeviceLogin_ShouldInvokeEmailService() {
        DeviceMetadata metadata = DeviceMetadata.builder()
                .browser("Chrome")
                .operatingSystem("Linux")
                .ipAddress("192.168.1.100")
                .location("Jakarta, ID")
                .build();

        NewDeviceLoginEvent event = NewDeviceLoginEvent.builder()
                .username("john_doe")
                .email("john@example.com")
                .deviceMetadata(metadata)
                .build();

        listener.onNewDeviceLogin(event);

        verify(emailService).sendNewDeviceLoginAlert("john_doe", "john@example.com", metadata);
    }

    @Test
    @DisplayName("onNewDeviceLogin catches any exception thrown by emailService gracefully")
    void onNewDeviceLogin_WhenEmailServiceThrows_ShouldNotRethrow() {
        DeviceMetadata metadata = DeviceMetadata.builder()
                .browser("Chrome")
                .operatingSystem("Linux")
                .ipAddress("192.168.1.100")
                .location("Jakarta, ID")
                .build();

        NewDeviceLoginEvent event = NewDeviceLoginEvent.builder()
                .username("john_doe")
                .email("john@example.com")
                .deviceMetadata(metadata)
                .build();

        doThrow(new RuntimeException("Mail server down")).when(emailService)
                .sendNewDeviceLoginAlert("john_doe", "john@example.com", metadata);

        assertThatCode(() -> listener.onNewDeviceLogin(event))
                .doesNotThrowAnyException();
    }
}
