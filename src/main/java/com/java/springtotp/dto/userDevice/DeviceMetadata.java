package com.java.springtotp.dto.userDevice;

import com.java.springtotp.util.Fingerprints;
import com.java.springtotp.util.IpUtils;
import lombok.Builder;

import java.util.Objects;

@Builder
public record DeviceMetadata(
        String ipAddress,
        String browser,
        String operatingSystem,
        String location,
        String deviceDetails,
        String fingerprint,
        String deviceToken
){
    public DeviceMetadata {
        Objects.requireNonNull(browser, "browser");
        Objects.requireNonNull(operatingSystem, "operatingSystem");

        ipAddress = IpUtils.maskIp(ipAddress);
        deviceDetails = browser + " on " + operatingSystem;
        fingerprint   = Fingerprints.sha256(deviceDetails);
    }

}
