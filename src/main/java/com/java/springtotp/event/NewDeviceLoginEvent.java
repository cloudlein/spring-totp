package com.java.springtotp.event;

import com.java.springtotp.dto.userDevice.DeviceMetadata;

import java.time.Instant;

public record NewDeviceLoginEvent(
      String username,
      String email,
      DeviceMetadata deviceMetadata
) {
}
