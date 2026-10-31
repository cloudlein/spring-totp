package com.java.springtotp.service.impl;

import com.java.springtotp.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@RequiredArgsConstructor
@Service
public class JwtServiceImpl implements JwtService {

    @Value("${application.security.jwt.secret-key}")
    private String secretKey;

    @Value("${application.security.jwt.expiration}")
    private long jwtExpiration;

    @Value("${application.security.jwt.refresh-token.expiration}")
    private long refreshExpiration;

    @Value("${application.security.jwt.mfa-expiration}")
    private long jwtMfaExpiration;

    @Override
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    @Override
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    @Override
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    @Override
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> extractClaims = new HashMap<>();
        return buildToken(extractClaims, userDetails, jwtExpiration);
    }

    @Override
    public String generateTokenRefresh(UserDetails userDetails) {
        return buildToken(new HashMap<>(), userDetails, refreshExpiration);
    }

    @Override
    public Boolean isValidToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    @Override
    public String generateMfaToken(UserDetails userDetails) {
        return Jwts.builder()
                .claim("type", "MFA_TEMP")
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtMfaExpiration))
                .signWith(getSignKey())
                .compact();
    }

    @Override
    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get("type", String.class));
    }


    private SecretKey getSignKey(){
        byte[] keyBytes = Base64.getDecoder().decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private String buildToken(
            Map<String, Object> extractClaims,
            UserDetails userDetails,
            long jwtExpiration
    ) {
        return Jwts.builder()
                .claims(extractClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSignKey())
                .compact();
    }
}
