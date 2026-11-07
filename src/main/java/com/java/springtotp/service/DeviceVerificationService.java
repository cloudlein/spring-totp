package com.java.springtotp.service;

import com.java.springtotp.model.User;
import jakarta.servlet.http.HttpServletRequest;

public interface DeviceVerificationService {

    void verifyDeviceAndNotify(User user, HttpServletRequest request);



}
