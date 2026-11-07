package com.java.springtotp.service;

import com.java.springtotp.dto.email.ResendEmailRequest;
import com.java.springtotp.dto.userDevice.DeviceMetadata;

public interface EmailService {

    void sendEmail(ResendEmailRequest request);
    void sendTotpEnabledAlert(String to, String username);

    void sendNEwDeviceLoginAlert(String username , String email, DeviceMetadata deviceMetadata);

}
