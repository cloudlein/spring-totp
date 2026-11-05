package com.java.springtotp.service;

import com.java.springtotp.dto.email.ResendEmailRequest;
import com.java.springtotp.properties.ResendProperties;
import com.java.springtotp.service.impl.EmailServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private TemplateEngine templateEngine;

    private MockRestServiceServer mockServer;
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = restClientBuilder.build();

        ResendProperties resendProperties = new ResendProperties(
                "re_test_key",
                "App <noreply@dev.com>",
                "https://api.resend.com"
        );

        emailService = new EmailServiceImpl(resendProperties, restClient, templateEngine);
    }

    @Test
    @DisplayName("sendEmail posts JSON payload to /emails endpoint and completes successfully")
    void sendEmail_ShouldDispatchPostRequest() {
        mockServer.expect(requestTo("/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("{\"id\":\"email_123\"}", MediaType.APPLICATION_JSON));

        ResendEmailRequest request = ResendEmailRequest.builder()
                .from("App <noreply@dev.com>")
                .to(List.of("user@example.com"))
                .subject("Test Subject")
                .htmlContent("<p>Test</p>")
                .build();

        emailService.sendEmail(request);

        mockServer.verify();
    }

    @Test
    @DisplayName("sendTotpEnabledAlert processes thymeleaf template and dispatches email via RestClient")
    void sendTotpEnabledAlert_ShouldRenderTemplateAndSendEmail() {
        when(templateEngine.process(eq("totp-enabled"), any(Context.class)))
                .thenReturn("<p>Security Alert</p>");

        mockServer.expect(requestTo("/emails"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"id\":\"email_456\"}", MediaType.APPLICATION_JSON));

        emailService.sendTotpEnabledAlert("user@example.com", "testuser");

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("totp-enabled"), contextCaptor.capture());

        Context capturedContext = contextCaptor.getValue();
        assertThat(capturedContext.getVariable("to")).isEqualTo("user@example.com");
        assertThat(capturedContext.getVariable("username")).isEqualTo("testuser");
        assertThat(capturedContext.getVariable("supportEmail")).isEqualTo("support@dev.com");
        assertThat(capturedContext.getVariable("enabledAt")).isNotNull();

        mockServer.verify();
    }

    @Test
    @DisplayName("sendTotpEnabledAlert contains exceptions gracefully when HTTP dispatch fails")
    void sendTotpEnabledAlert_WhenHttpFails_ShouldNotPropagateException() {
        when(templateEngine.process(eq("totp-enabled"), any(Context.class)))
                .thenReturn("<p>Security Alert</p>");

        mockServer.expect(requestTo("/emails"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatCode(() -> emailService.sendTotpEnabledAlert("user@example.com", "testuser"))
                .doesNotThrowAnyException();

        mockServer.verify();
    }
}
