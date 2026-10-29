package com.java.springtotp.service;

public interface TokenBlacklistService {

    void addToBlacklist(String token, long expirationTimeMillis);
    boolean isBlacklisted(String token);

}
