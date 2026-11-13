package com.java.springtotp.dto.captcha;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TurnstileResponse {

    boolean success;

    @JsonProperty("error-codes")
    List<String> errorCodes;

    @JsonProperty("challenge_ts")
    String challengeTs;

    String hostname;
    String action;
    String cdata;

}
