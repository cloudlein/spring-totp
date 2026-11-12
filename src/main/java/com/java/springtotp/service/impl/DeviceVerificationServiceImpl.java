package com.java.springtotp.service.impl;

import com.java.springtotp.dto.userDevice.DeviceMetadata;
import com.java.springtotp.event.NewDeviceLoginEvent;
import com.java.springtotp.model.User;
import com.java.springtotp.model.UserDevice;
import com.java.springtotp.repository.UserDeviceRepository;
import com.java.springtotp.service.DeviceDetectionService;
import com.java.springtotp.service.DeviceVerificationService;
import com.java.springtotp.service.EmailService;
import com.java.springtotp.util.Fingerprints;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class DeviceVerificationServiceImpl implements DeviceVerificationService {

    private final DeviceDetectionService deviceDetectionService;
    private final UserDeviceRepository userDeviceRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void verifyDeviceAndNotify(User user, HttpServletRequest request) {
        DeviceMetadata metadata = deviceDetectionService.extractMetaData(request);
        String deviceHash = resolveDeviceHash(metadata);

        Optional<UserDevice> existingDevice = (deviceHash == null)
                ? Optional.empty()
                : userDeviceRepository.findByUserIdAndDeviceHash(user.getId(), deviceHash);

        existingDevice.ifPresentOrElse(
                device -> {
                    device.setLastLoginAt(Instant.now());
                    device.setIpAddress(metadata.ipAddress());
                    device.setLocation(metadata.location());
                    device.setFingerPrint(metadata.fingerprint());
                    userDeviceRepository.save(device);
                },
                () -> {
                    UserDevice newDevice = UserDevice.builder()
                            .user(user)
                            .deviceDetails(metadata.deviceDetails())
                            .deviceHash(deviceHash)
                            .ipAddress(metadata.ipAddress())
                            .location(metadata.location())
                            .fingerPrint(metadata.fingerprint())
                            .lastLoginAt(Instant.now())
                            .build();
                    userDeviceRepository.save(newDevice);
                   eventPublisher.publishEvent(
                           NewDeviceLoginEvent.builder()
                                   .username(user.getUsername())
                                   .email(user.getEmail())
                                   .deviceMetadata(metadata)
                                   .build()
                   );
                }
        );
    }

    private String resolveDeviceHash(DeviceMetadata deviceMetadata) {
        String token = deviceMetadata.deviceToken();
        String source = (token != null) && !token.isBlank() ? token : deviceMetadata.fingerprint();
        if (source == null || source.isBlank()) {
            throw new IllegalStateException(
                    "Cannot derive deviceHash: deviceToken and fingerprint are both empty");
        }

        return Fingerprints.sha256(source);
    }
}
