package com.java.springtotp.service;

public interface TotpService {

    String generateSecretKey();
    String generateQrCodeDataUri(String otpAuthUri);

    boolean verifyCode(String secret, String code );

    String buildOtpAuthUri(String secret, String accountName, String issuer);
}
