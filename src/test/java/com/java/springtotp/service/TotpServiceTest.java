package com.java.springtotp.service;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import com.java.springtotp.service.impl.TotpServiceImpl;
import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {

    private TotpService totpService;
    private TimeBasedOneTimePasswordGenerator otpGenerator;

    @BeforeEach
    void setUp() {
        totpService = new TotpServiceImpl();
        otpGenerator = new TimeBasedOneTimePasswordGenerator();
    }

    @Test
    @DisplayName("generateSecretKey returns non-blank Base32 encoded string")
    void generateSecretKey_ShouldReturnValidBase32Secret() {
        String secret = totpService.generateSecretKey();

        assertThat(secret).isNotBlank();
        assertThat(secret).matches("^[A-Z2-7]+$");
    }

    @Test
    @DisplayName("buildOtpAuthUri returns compliant RFC 6238 URI with encoded characters")
    void buildOtpAuthUri_ShouldReturnFormattedUri() {
        String secret = "JBSWY3DPEHPK3PXP";
        String accountName = "john doe";
        String issuer = "Spring TOTP";

        String uri = totpService.buildOtpAuthUri(secret, accountName, issuer);

        assertThat(uri).isEqualTo("otpauth://totp/Spring%20TOTP:john%20doe?secret=JBSWY3DPEHPK3PXP&issuer=Spring%20TOTP&algorithm=SHA1&digits=6&period=30");
    }

    @Test
    @DisplayName("generateQrCodeDataUri returns valid Base64 PNG data URI")
    void generateQrCodeDataUri_ShouldReturnBase64PngDataUri() {
        String otpAuthUri = "otpauth://totp/Test:user?secret=JBSWY3DPEHPK3PXP&issuer=Test";

        String qrDataUri = totpService.generateQrCodeDataUri(otpAuthUri);

        assertThat(qrDataUri).startsWith("data:image/png;base64,");

        String base64Content = qrDataUri.substring("data:image/png;base64,".length());
        byte[] decodedBytes = Base64.getDecoder().decode(base64Content);
        assertThat(decodedBytes).isNotEmpty();
    }

    @Test
    @DisplayName("verifyCode returns true for current valid OTP code")
    void verifyCode_WithValidCurrentCode_ShouldReturnTrue() throws InvalidKeyException {
        String secret = totpService.generateSecretKey();
        String currentCode = generateOtp(secret, Instant.now());

        boolean isValid = totpService.verifyCode(secret, currentCode);

        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("verifyCode returns true within past 30-second clock drift window")
    void verifyCode_WithinPastWindow_ShouldReturnTrue() throws InvalidKeyException {
        String secret = totpService.generateSecretKey();
        String pastCode = generateOtp(secret, Instant.now().minus(Duration.ofSeconds(30)));

        boolean isValid = totpService.verifyCode(secret, pastCode);

        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("verifyCode returns true within future 30-second clock drift window")
    void verifyCode_WithinFutureWindow_ShouldReturnTrue() throws InvalidKeyException {
        String secret = totpService.generateSecretKey();
        String futureCode = generateOtp(secret, Instant.now().plus(Duration.ofSeconds(30)));

        boolean isValid = totpService.verifyCode(secret, futureCode);

        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("verifyCode returns false outside clock drift tolerance")
    void verifyCode_OutsideWindow_ShouldReturnFalse() throws InvalidKeyException {
        String secret = totpService.generateSecretKey();
        String pastCode = generateOtp(secret, Instant.now().minus(Duration.ofSeconds(60)));
        String futureCode = generateOtp(secret, Instant.now().plus(Duration.ofSeconds(60)));

        assertThat(totpService.verifyCode(secret, pastCode)).isFalse();
        assertThat(totpService.verifyCode(secret, futureCode)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"12345", "1234567", "abcdef", "12a456", " "})
    @DisplayName("verifyCode returns false for malformed or non-6-digit OTP codes")
    void verifyCode_WithInvalidCodeFormat_ShouldReturnFalse(String invalidCode) {
        String secret = totpService.generateSecretKey();

        boolean isValid = totpService.verifyCode(secret, invalidCode);

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("verifyCode returns false when secret is null or invalid Base32")
    void verifyCode_WithInvalidSecret_ShouldReturnFalse() {
        assertThat(totpService.verifyCode(null, "123456")).isFalse();
        assertThat(totpService.verifyCode("INVALID-BASE32!", "123456")).isFalse();
    }

    private String generateOtp(String secret, Instant timestamp) throws InvalidKeyException {
        SecretKey secretKey = new SecretKeySpec(new Base32().decode(secret), "HmacSHA1");
        int otp = otpGenerator.generateOneTimePassword(secretKey, timestamp);
        return String.format("%06d", otp);
    }
}
