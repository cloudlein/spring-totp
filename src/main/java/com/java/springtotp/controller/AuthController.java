package com.java.springtotp.controller;

import com.java.springtotp.dto.ApiResponse;
import com.java.springtotp.dto.auth.*;
import com.java.springtotp.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        return ApiResponse.success(authService.login(loginRequest));
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest registerRequest) {
        return ApiResponse.success(authService.register(registerRequest));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader("Authorization") String authHeader) {
        if ( authHeader == null || !authHeader.startsWith("Bearer ")) {
           throw new IllegalArgumentException("Authorization header is invalid");
        }

        authService.logout(authHeader.substring(7));
        return ApiResponse.success("logout success", null);
    }

    @PostMapping("/refresh-token")
    public ApiResponse<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest refreshTokenRequest) {
        return ApiResponse.success( authService.refreshToken(refreshTokenRequest.getRefreshToken()));
    }

    @GetMapping("/totp/setup")
    public ApiResponse<TotpSetupResponse> totpSetup(@AuthenticationPrincipal UserDetails userDetails) {
        TotpSetupResponse response = authService.setupTotp(userDetails.getUsername());
        return ApiResponse.success("success setup totp", response);
    }

    @PostMapping("/totp/enable")
    public ApiResponse<Void> enableTotp(
            @Valid @RequestBody TotpEnableRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        authService.enableTotp(userDetails.getUsername(), request.getCode());
        return ApiResponse.success("success enable totp", null);
    }

    @PostMapping("/totp/verify")
    public ApiResponse<AuthResponse> verifyTotp(@Valid @RequestBody TotpVerifyRequest request) {
        AuthResponse response = authService.verifyTotpLogin(request);
        return ApiResponse.success("success verify totp", response);
    }

}
