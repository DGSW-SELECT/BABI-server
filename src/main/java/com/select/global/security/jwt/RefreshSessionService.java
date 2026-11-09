package com.select.global.security.jwt;

import com.select.domain.user.exception.AccountLifecycleException;
import com.select.global.exception.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/** Stores only hashes of opaque, single-use refresh credentials. */
@Service
public class RefreshSessionService {
    public static final Duration LIFETIME = Duration.ofDays(14);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DefaultRedisScript<String> ROTATE = new DefaultRedisScript<>("""
            local session = redis.call('GET', KEYS[1])
            if not session then return nil end
            local user, digest = string.match(session, '^(%d+):([0-9a-f]+)$')
            if not user or digest ~= ARGV[1] then return nil end
            redis.call('SET', KEYS[1], user .. ':' .. ARGV[2], 'EX', ARGV[3])
            return user
            """, String.class);
    private static final DefaultRedisScript<Long> REVOKE = new DefaultRedisScript<>("""
            local session = redis.call('GET', KEYS[1])
            if not session then return 0 end
            local user, digest = string.match(session, '^(%d+):([0-9a-f]+)$')
            if not user or digest ~= ARGV[1] then return 0 end
            return redis.call('DEL', KEYS[1])
            """, Long.class);
    private final StringRedisTemplate redis;

    public RefreshSessionService(StringRedisTemplate redis) { this.redis = redis; }

    public String create(String userId) {
        String id = randomToken();
        String secret = randomToken();
        try { redis.opsForValue().set(key(id), userId + ":" + hash(secret), LIFETIME); }
        catch (org.springframework.dao.DataAccessException e) { throw unavailable(); }
        return id + "." + secret;
    }

    public Rotation rotate(String oldToken) {
        if (!validFormat(oldToken)) throw invalid();
        String[] parts = oldToken.split("\\.");
        String next = randomToken();
        try {
            String userId = redis.execute(ROTATE, List.of(key(parts[0])), hash(parts[1]), hash(next), Long.toString(LIFETIME.toSeconds()));
            if (userId == null) throw invalid();
            return new Rotation(userId, parts[0] + "." + next);
        } catch (org.springframework.dao.DataAccessException e) { throw unavailable(); }
    }

    public void revoke(String token) {
        if (!validFormat(token)) return;
        String[] parts = token.split("\\.");
        try { redis.execute(REVOKE, List.of(key(parts[0])), hash(parts[1])); }
        catch (org.springframework.dao.DataAccessException e) { throw unavailable(); }
    }

    public void revokeSession(String sessionId) {
        if (sessionId == null || !sessionId.matches("[A-Za-z0-9_-]{43}")) return;
        try { redis.delete(key(sessionId)); }
        catch (org.springframework.dao.DataAccessException e) { throw unavailable(); }
    }

    public boolean isActive(String sessionId) {
        if (sessionId == null || !sessionId.matches("[A-Za-z0-9_-]{43}")) return false;
        Boolean active = redis.hasKey(key(sessionId));
        if (active == null) throw new org.springframework.dao.DataAccessResourceFailureException("Refresh session unavailable");
        return active;
    }

    public static String sessionId(String token) {
        if (!validFormat(token)) throw invalid();
        return token.substring(0, 43);
    }

    private static boolean validFormat(String token) { return token != null && token.matches("[A-Za-z0-9_-]{43}\\.[A-Za-z0-9_-]{43}"); }
    private static String randomToken() {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private static String key(String id) { return "auth:refresh:" + hash(id); }
    private static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static AccountLifecycleException invalid() { return new AccountLifecycleException(ErrorCode.INVALID_TOKEN); }
    private static AccountLifecycleException unavailable() { return new AccountLifecycleException(ErrorCode.AUTH_SERVICE_UNAVAILABLE); }
    public record Rotation(String userId, String token) { }
}
