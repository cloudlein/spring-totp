package com.java.springtotp.dto.email;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ResendEmailRequest {

    private String from;
    private List<String> to;
    private String subject;
    private String htmlContent;
}
