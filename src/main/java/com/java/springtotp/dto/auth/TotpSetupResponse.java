package com.java.springtotp.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TotpSetupResponse {

    private String secretKey;
    private String qrCodeDataUrl;
    private String manualUri;

}
