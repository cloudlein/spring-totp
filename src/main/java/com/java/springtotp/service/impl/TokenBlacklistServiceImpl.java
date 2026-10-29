package com.java.springtotp.service.impl;

import com.java.springtotp.service.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@RequiredArgsConstructor
@Service
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

    private final StringRedisTemplate stringRedisTemplate;
    private static final String TOKEN_BLACKLIST_PREFIX = "blacklist:jwt:";

    @Override
    public void addToBlacklist(String token, long expirationTimeMillis) {
        String key = TOKEN_BLACKLIST_PREFIX + token;
        String value = "blacklisted";
        stringRedisTemplate.opsForValue().set(key, value, Duration.ofMillis(expirationTimeMillis));
    }

    @Override
    public boolean isBlacklisted(String token) {
        String key = TOKEN_BLACKLIST_PREFIX + token;
        Boolean hashKey = stringRedisTemplate.hasKey(key);
        return Boolean.TRUE.equals(hashKey);
    }
}
