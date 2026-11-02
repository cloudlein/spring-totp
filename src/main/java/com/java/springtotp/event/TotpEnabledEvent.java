package com.java.springtotp.event;

import java.time.Instant;

public record  TotpEnabledEvent (
         String email,
         String username,
         Instant timestamp
) {

}

