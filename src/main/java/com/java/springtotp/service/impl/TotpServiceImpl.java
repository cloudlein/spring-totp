package com.java.springtotp.service.impl;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import com.java.springtotp.service.TotpService;
import lombok.RequiredArgsConstructor;
import net.glxn.qrgen.javase.QRCode;
import org.apache.commons.codec.binary.Base32;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

@RequiredArgsConstructor
@Service
public class TotpServiceImpl implements TotpService {


    @Override
    public String generateSecretKey() {
        byte[] key = KeyGenerators.secureRandom().generateKey();
        return new Base32().encodeToString(key).replace("=", "");
    }

    @Override
    public String generateQrCodeDataUri(String otpAuthUrl) {

        byte[] bytes = QRCode.from(otpAuthUrl)
                .withSize(250, 250)
                .stream()
                .toByteArray();

        return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
    }

    @Override
    public boolean verifyCode(String secret, String code) {
        if (code == null || !code.matches("\\d{6}") || secret == null) {
            return false;
        }

        try {
            TimeBasedOneTimePasswordGenerator totp = new  TimeBasedOneTimePasswordGenerator();

            SecretKey secretKey = new SecretKeySpec(new Base32().decode(secret),"HmacSHA1");

            Instant now = Instant.now();

            for (int i = -1 ; i <= 1; i++) {
                Instant t = now.plus(totp.getTimeStep().multipliedBy(i));
                String expected = String.format("%06d", totp.generateOneTimePassword(secretKey, t));
                if (MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), code.getBytes(StandardCharsets.UTF_8) )) {
                    return true;
                }
            }

            return false;
        } catch (InvalidKeyException e) {
            return false;
        }

    }

    @Override
    public String buildOtpAuthUri(String secret, String accountName, String issuer) {
        String encIssuer = encode(issuer);
        String encAccountName =  encode(accountName);

        return "otpauth://totp/" + encIssuer + ":" + encAccountName
                + "?secret=" + secret
                + "&issuer=" + encIssuer
                + "&algorithm=SHA1&digits=6&period=30";
    }


    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
