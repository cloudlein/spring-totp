package com.java.springtotp.service;

import com.java.springtotp.dto.userDevice.DeviceMetadata;
import jakarta.servlet.http.HttpServletRequest;

public interface DeviceDetectionService {

    DeviceMetadata extractMetaData(HttpServletRequest request);

}
