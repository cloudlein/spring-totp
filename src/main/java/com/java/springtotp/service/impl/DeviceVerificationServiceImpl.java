package com.java.springtotp.service.impl;

import com.java.springtotp.dto.userDevice.DeviceMetadata;
import com.java.springtotp.model.User;
import com.java.springtotp.model.UserDevice;
import com.java.springtotp.repository.UserDeviceRepository;
import com.java.springtotp.service.DeviceDetectionService;
import com.java.springtotp.service.DeviceVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class DeviceVerificationServiceImpl implements DeviceVerificationService {

    private final DeviceDetectionService deviceDetectionService;
    private final UserDeviceRepository userDeviceRepository;


    @Override
    public void verifyDeviceAndNotify(User user, HttpServletRequest request) {
        DeviceMetadata metadata = deviceDetectionService.extractMetaData(request);
        Optional<UserDevice> existingDevice = userDeviceRepository.findByUserIdAndDeviceHash(user.getId(), metadata.fingerprint());
        existingDevice.ifPresentOrElse(
                device -> {
                    device.setLastLoginAt(Instant.now());
                    device.setIpAddress(metadata.ipAddress());
                    device.setLocation(metadata.location());
                    userDeviceRepository.save(device);
                },
                () -> {
//                    UserDevice newDevice = UserDevice.builder()
//                            .user(user)
//                            .deviceDetails(metadata.deviceDetails())
//                            .deviceHash(metadata.fingerprint())
//                            .ipAddress(metadata.ipAddress())
//                            .location(metadata.location())
//                            .
//                            .build();
                }
        );
    }
}
