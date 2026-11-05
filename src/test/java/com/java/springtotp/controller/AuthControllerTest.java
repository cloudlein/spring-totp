package com.java.springtotp.controller;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.springtotp.dto.auth.LoginRequest;
import com.java.springtotp.dto.auth.RegisterRequest;
import com.java.springtotp.dto.auth.TotpEnableRequest;
import com.java.springtotp.dto.auth.TotpVerifyRequest;
import com.java.springtotp.service.EmailService;
import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmailService emailService;

    private static String registeredUsername;
    private static String registeredPassword;
    private static String registeredEmail;
    private static String accessToken;
    private static String totpSecret;

    @Test
    @Order(1)
    @DisplayName("Test Case 1: Initial Registration & Login")
    void testCase1_initialRegistrationAndLogin() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        registeredUsername = "user_" + uniqueSuffix;
        registeredEmail = "user_" + uniqueSuffix + "@example.com";
        registeredPassword = "Password123!";

        RegisterRequest registerRequest = RegisterRequest.builder()
                .name("Test User " + uniqueSuffix)
                .username(registeredUsername)
                .email(registeredEmail)
                .password(registeredPassword)
                .confirmPassword(registeredPassword)
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        LoginRequest loginRequest = LoginRequest.builder()
                .username(registeredUsername)
                .password(registeredPassword)
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(responseBody);

        accessToken = root.path("data").path("token").asText();
        assertThat(accessToken).isNotBlank();

        JsonNode mfaRequiredNode = root.path("data").path("mfaRequired");
        if (!mfaRequiredNode.isMissingNode() && !mfaRequiredNode.isNull()) {
            assertThat(mfaRequiredNode.asBoolean()).isFalse();
        }
    }

    @Test
    @Order(2)
    @DisplayName("Test Case 2: Attempt Immediate Logout (Expected Failure)")
    void testCase2_attemptImmediateLogoutWithoutTotp() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("TOTP Two-Factor Authentication Required"));
    }

    @Test
    @Order(3)
    @DisplayName("Test Case 3: Provision TOTP Secret & QR Code")
    void testCase3_provisionTotpSecretAndQrCode() throws Exception {
        MvcResult setupResult = mockMvc.perform(get("/api/v1/auth/totp/setup")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.secretKey").isNotEmpty())
                .andExpect(jsonPath("$.data.qrCodeDataUrl").isNotEmpty())
                .andReturn();

        String responseBody = setupResult.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(responseBody);

        totpSecret = root.path("data").path("secretKey").asText();
        String qrCodeUrl = root.path("data").path("qrCodeDataUrl").asText();

        assertThat(totpSecret).isNotBlank();
        assertThat(qrCodeUrl).startsWith("data:image/png;base64,");
    }

    @Test
    @Order(4)
    @DisplayName("Test Case 4: Enable TOTP with Invalid vs Valid Code")
    void testCase4_enableTotpWithInvalidAndValidCode() throws Exception {
        TotpEnableRequest invalidRequest = TotpEnableRequest.builder()
                .code("000000")
                .build();

        mockMvc.perform(post("/api/v1/auth/totp/enable")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("invalid TOTP verification code"));

        String validOtpCode = generateCurrentOtp(totpSecret);
        TotpEnableRequest validRequest = TotpEnableRequest.builder()
                .code(validOtpCode)
                .build();

        mockMvc.perform(post("/api/v1/auth/totp/enable")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(emailService, timeout(3000).times(1))
                .sendTotpEnabledAlert(eq(registeredEmail), eq(registeredUsername));
    }

    @Test
    @Order(5)
    @DisplayName("Test Case 5: Retry Logout (Expected Success)")
    void testCase5_retryLogoutAfterTotpEnabled() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("logout success"));
    }

    @Test
    @Order(6)
    @DisplayName("Test Case 6: Login Flow with MFA Enforced")
    void testCase6_loginFlowWithMfaEnforced() throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .username(registeredUsername)
                .password(registeredPassword)
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.mfaRequired").value(true))
                .andExpect(jsonPath("$.data.tempToken").isNotEmpty())
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(responseBody);
        String tempToken = root.path("data").path("tempToken").asText();
        assertThat(tempToken).isNotBlank();

        String validOtpCode = generateCurrentOtp(totpSecret);
        TotpVerifyRequest verifyRequest = TotpVerifyRequest.builder()
                .tempToken(tempToken)
                .code(validOtpCode)
                .build();

        mockMvc.perform(post("/api/v1/auth/totp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
    }

    @Test
    @Order(7)
    @DisplayName("Test Case 7: Asynchronous Security Email Dispatch on TOTP Activation")
    void testCase7_asyncSecurityEmailDispatchOnTotpActivation() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "async_" + suffix + "@example.com";
        String username = "async_" + suffix;
        String password = "Password123!";

        RegisterRequest registerRequest = RegisterRequest.builder()
                .name("Async User " + suffix)
                .username(username)
                .email(email)
                .password(password)
                .confirmPassword(password)
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk());

        LoginRequest loginRequest = LoginRequest.builder()
                .username(username)
                .password(password)
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String userToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("data").path("token").asText();

        MvcResult setupResult = mockMvc.perform(get("/api/v1/auth/totp/setup")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();

        String secret = objectMapper.readTree(setupResult.getResponse().getContentAsString())
                .path("data").path("secretKey").asText();

        String otp = generateCurrentOtp(secret);
        TotpEnableRequest enableRequest = TotpEnableRequest.builder().code(otp).build();

        mockMvc.perform(post("/api/v1/auth/totp/enable")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(enableRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(emailService, timeout(3000).times(1))
                .sendTotpEnabledAlert(eq(email), eq(username));
    }

    private String generateCurrentOtp(String secret) throws InvalidKeyException {
        TimeBasedOneTimePasswordGenerator otpGenerator = new TimeBasedOneTimePasswordGenerator();
        SecretKey secretKey = new SecretKeySpec(new Base32().decode(secret), "HmacSHA1");
        int otp = otpGenerator.generateOneTimePassword(secretKey, Instant.now());
        return String.format("%06d", otp);
    }
}
