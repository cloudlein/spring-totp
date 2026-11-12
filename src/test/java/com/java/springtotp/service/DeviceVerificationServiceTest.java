package com.java.springtotp.service;

import com.java.springtotp.dto.userDevice.DeviceMetadata;
import com.java.springtotp.event.NewDeviceLoginEvent;
import com.java.springtotp.model.User;
import com.java.springtotp.model.UserDevice;
import com.java.springtotp.repository.UserDeviceRepository;
import com.java.springtotp.service.impl.DeviceVerificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceVerificationServiceTest {

    @Mock
    private DeviceDetectionService deviceDetectionService;

    @Mock
    private UserDeviceRepository userDeviceRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DeviceVerificationServiceImpl deviceVerificationService;

    private User testUser;
    private MockHttpServletRequest request;
    private DeviceMetadata metadata;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("john_doe")
                .email("john@example.com")
                .build();

        request = new MockHttpServletRequest();
        metadata = DeviceMetadata.builder()
                .browser("Chrome")
                .operatingSystem("Linux")
                .ipAddress("192.168.1.100")
                .location("Jakarta, ID")
                .deviceToken("sample-token-123")
                .build();
    }

    @Test
    @DisplayName("New Device: saves new UserDevice entity with lastLoginAt and publishes NewDeviceLoginEvent")
    void verifyDeviceAndNotify_WhenDeviceIsNew_ShouldSaveAndPublishEvent() {
        when(deviceDetectionService.extractMetaData(request)).thenReturn(metadata);
        when(userDeviceRepository.findByUserIdAndDeviceHash(eq(1L), anyString()))
                .thenReturn(Optional.empty());

        deviceVerificationService.verifyDeviceAndNotify(testUser, request);

        ArgumentCaptor<UserDevice> deviceCaptor = ArgumentCaptor.forClass(UserDevice.class);
        verify(userDeviceRepository).save(deviceCaptor.capture());
        UserDevice savedDevice = deviceCaptor.getValue();

        assertThat(savedDevice.getUser()).isEqualTo(testUser);
        assertThat(savedDevice.getDeviceDetails()).isEqualTo("Chrome on Linux");
        assertThat(savedDevice.getIpAddress()).isEqualTo("192.168.1.xxx");
        assertThat(savedDevice.getLastLoginAt()).isNotNull();

        ArgumentCaptor<NewDeviceLoginEvent> eventCaptor = ArgumentCaptor.forClass(NewDeviceLoginEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
        NewDeviceLoginEvent publishedEvent = eventCaptor.getValue();

        assertThat(publishedEvent.username()).isEqualTo("john_doe");
        assertThat(publishedEvent.email()).isEqualTo("john@example.com");
    }

    @Test
    @DisplayName("Known Device (Alert Fatigue Prevention): updates metadata and NEVER publishes event")
    void verifyDeviceAndNotify_WhenDeviceIsKnown_ShouldUpdateWithoutPublishingEvent() {
        UserDevice existingDevice = UserDevice.builder()
                .id(10L)
                .user(testUser)
                .deviceHash("hash123")
                .build();

        when(deviceDetectionService.extractMetaData(request)).thenReturn(metadata);
        when(userDeviceRepository.findByUserIdAndDeviceHash(eq(1L), anyString()))
                .thenReturn(Optional.of(existingDevice));

        deviceVerificationService.verifyDeviceAndNotify(testUser, request);

        verify(userDeviceRepository).save(existingDevice);
        verify(eventPublisher, never()).publishEvent(any());
        assertThat(existingDevice.getIpAddress()).isEqualTo("192.168.1.xxx");
        assertThat(existingDevice.getLocation()).isEqualTo("Jakarta, ID");
    }
}
