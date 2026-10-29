package com.java.springtotp.service;

import io.jsonwebtoken.Claims;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Date;
import java.util.function.Function;

public interface JwtService {

    String extractUsername(String token);
    Date extractExpiration(String token);
    <T> T extractClaim(String token, Function<Claims, T> claimsResolver);
    String generateToken(UserDetails userDetails);
    String generateTokenRefresh(UserDetails userDetails);
    Boolean isValidToken(String token, UserDetails userDetails);

    String generateMfaToken(UserDetails userDetails);

    String extractTokenType(String token);
}
