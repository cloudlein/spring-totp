package com.java.springtotp.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.springtotp.dto.auth.LoginRequest;
import com.java.springtotp.dto.auth.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

    protected static String registeredUsername;
    protected static String registeredPassword;
    protected static String registeredEmail;
    protected static String accessToken;

    @Test
    @Order(1)
    @DisplayName("Test Case 1: Initial Registration & Login")
    void testCase1_initialRegistrationAndLogin() throws Exception {
        // 1. Arrange: Prepare unique user credentials
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

        // 2. Dispatch POST /api/v1/auth/register
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 3. Dispatch POST /api/v1/auth/login using the newly registered credentials
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

        // 4. Assert data.token is present and non-empty, and data.mfaRequired evaluates to false (or absent/null)
        String responseBody = loginResult.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(responseBody);

        accessToken = root.path("data").path("token").asText();
        assertThat(accessToken).isNotBlank();

        JsonNode mfaRequiredNode = root.path("data").path("mfaRequired");
        if (!mfaRequiredNode.isMissingNode() && !mfaRequiredNode.isNull()) {
            assertThat(mfaRequiredNode.asBoolean()).isFalse();
        }
    }
}
