package com.java.springtotp.dto.captcha;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class TurnstileRequest {

    private String secret;
    private String response;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String remoteIp;

}
