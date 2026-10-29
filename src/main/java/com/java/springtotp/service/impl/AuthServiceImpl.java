package com.java.springtotp.service.impl;

import com.java.springtotp.dto.auth.*;
import com.java.springtotp.exception.BadRequestException;
import com.java.springtotp.exception.InvalidTokenException;
import com.java.springtotp.exception.ResourceAlreadyExistsException;
import com.java.springtotp.exception.ResourceNotFoundException;
import com.java.springtotp.mapper.UserMapper;
import com.java.springtotp.model.Role;
import com.java.springtotp.model.User;
import com.java.springtotp.repository.RoleRepository;
import com.java.springtotp.repository.UserRepository;
import com.java.springtotp.service.*;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserDetailsService userDetailsService;
    private final TotpService totpService;

    @Value("${APPLICATION_NAME}")
    private String applicationName;


    @Override
    public AuthResponse login(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(), loginRequest.getPassword()
                )
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = getUserByUsername(userDetails.getUsername());

        if (!user.getIsTotpEnabled() || user.getIsTotpEnabled() == null) {
            String accessToken = jwtService.generateToken(userDetails);
            String refreshToken = jwtService.generateTokenRefresh(userDetails);

            return AuthResponse.builder()
                    .message("login successful")
                    .token(accessToken)
                    .refreshToken(refreshToken)
                    .build();
        }

        String ephemeralToken = jwtService.generateMfaToken(userDetails);

        return AuthResponse.builder()
                .message("mfa required")
                .mfaRequired(true)
                .tempToken(ephemeralToken)
                .build();
    }

    @Transactional()
    @Override
    public AuthResponse register(RegisterRequest registerRequest) {
        if (userRepository.existsByName(registerRequest.getName())) {
            throw new ResourceAlreadyExistsException("Name already exists");
        }

        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new ResourceAlreadyExistsException("username already exists");
        }

        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new ResourceAlreadyExistsException("email already exists");
        }

        User user = userMapper.toEntity(registerRequest);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        Role role = roleRepository.findByRole("USER")
                .orElseThrow(() -> new ResourceNotFoundException("role not found"));
        user.setRole(role);
        userRepository.save(user);

        return AuthResponse.builder()
                .message("register successful")
                .build();
    }


    @Override
    public void logout(String token) {
        String username = jwtService.extractUsername(token);
        User user = getUserByUsername(username);

        Boolean isTotpEnabled = user.getIsTotpEnabled();

        if (user.getIsTotpEnabled() == null || !isTotpEnabled) {
            throw new BadRequestException("TOTP Two-Factor Authentication Required");
        }

        long remainingMillis = jwtService.extractExpiration(token).getTime() - System.currentTimeMillis();

        if (remainingMillis > 0) {
            tokenBlacklistService.addToBlacklist(token,remainingMillis);
        }
        SecurityContextHolder.clearContext();
    }

    @Override
    public AuthResponse refreshToken(String refreshToken) {
        if (tokenBlacklistService.isBlacklisted(refreshToken)) {
            throw new InvalidTokenException("invalid token");
        }

        String username = jwtService.extractUsername(refreshToken);
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        if (!jwtService.isValidToken(refreshToken, userDetails)) {
            throw new InvalidTokenException("invalid token");
        }

        String newAccessToken = jwtService.generateToken(userDetails);
        String newRefreshToken = jwtService.generateTokenRefresh(userDetails);

        return AuthResponse.builder()
                .message("refresh successful")
                .token(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();


    }

    @Override
    public TotpSetupResponse setupTotp(String username) {
        User user =  getUserByUsername(username);

        if (!StringUtils.isNotEmpty(user.getTotpSecret())) {
            user.setTotpSecret(totpService.generateSecretKey());
            user.setIsTotpEnabled(false);
            userRepository.save(user);
        }

        String userSecretKey = user.getTotpSecret();
        String otpAuthUri = totpService.buildOtpAuthUri(userSecretKey, user.getUsername(), applicationName);

        return TotpSetupResponse.builder()
                .qrCodeDataUrl(totpService.generateQrCodeDataUri(otpAuthUri))
                .secretKey(userSecretKey)
                .manualUri(otpAuthUri)
                .build();

    }

    @Override
    public void enableTotp(String username, String code) {
        User user = getUserByUsername(username);

        if (!totpService.verifyCode(code, user.getTotpSecret())) {
            throw new BadRequestException("invalid TOTP verification code");
        }

        user.setIsTotpEnabled(true);
        userRepository.save(user);

    }

    @Override
    public AuthResponse verifyTotpLogin(TotpVerifyRequest totpVerifyRequest) {

        String username = jwtService.extractUsername(totpVerifyRequest.getTempToken());
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        User user = getUserByUsername(username);

         if (!"MFA_TEMP".equals(jwtService.extractTokenType(totpVerifyRequest.getTempToken())) || !jwtService.isValidToken(totpVerifyRequest.getTempToken(), userDetails)) {
             throw new BadRequestException("invalid TOTP verification code");
         }

         if (!totpService.verifyCode(user.getTotpSecret(), totpVerifyRequest.getCode())) {
             throw new BadRequestException("invalid TOTP verification code");
         }

        String accessToken = jwtService.generateToken(userDetails);
        String refreshToken = jwtService.generateTokenRefresh(userDetails);

        return AuthResponse.builder()
                .message("login successful")
                .token(accessToken)
                .refreshToken(refreshToken)
                .build();

    }


    private User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("user not found"));
    }
}
