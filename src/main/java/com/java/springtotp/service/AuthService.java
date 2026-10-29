package com.java.springtotp.service;

import com.java.springtotp.dto.ApiResponse;
import com.java.springtotp.dto.auth.*;

public interface AuthService {

    AuthResponse login(LoginRequest loginRequest);
    AuthResponse register(RegisterRequest registerRequest);
    void logout(String token);
    AuthResponse refreshToken(String refreshToken);

    TotpSetupResponse setupTotp(String username);

    void enableTotp(String username, String code);

    AuthResponse verifyTotpLogin(TotpVerifyRequest totpVerifyRequest);
}
