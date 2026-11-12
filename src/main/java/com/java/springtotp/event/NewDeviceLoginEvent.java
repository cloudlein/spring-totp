package com.java.springtotp.event;

import com.java.springtotp.dto.userDevice.DeviceMetadata;
import lombok.Builder;

import java.time.Instant;

@Builder
public record NewDeviceLoginEvent(
      String username,
      String email,
      DeviceMetadata deviceMetadata
) {
}
