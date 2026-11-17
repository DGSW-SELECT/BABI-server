package com.select.global.security.jwt;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

@Service
public class TokenRevocationService {
    private final StringRedisTemplate redis;
    public TokenRevocationService(StringRedisTemplate redis) { this.redis = redis; }
    public boolean isRevoked(String token) {
        Boolean result = redis.hasKey(key(token));
        if (result == null) throw new org.springframework.dao.DataAccessResourceFailureException("Token revocation unavailable");
        return result;
    }
    public void revoke(String token, Duration remaining) {
        if (!remaining.isZero() && !remaining.isNegative()) redis.opsForValue().set(key(token), "1", remaining);
    }
    private String key(String token) {
        try { return "auth:revoked:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
}
