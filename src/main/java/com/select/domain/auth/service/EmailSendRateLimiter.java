package com.select.domain.auth.service;

import com.select.domain.auth.exception.EmailVerificationException;
import com.select.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Fixed-window limits shared by all application instances. */
@Component
public class EmailSendRateLimiter {
    private static final int WINDOW_SECONDS = 60;
    private static final DefaultRedisScript<Long> LIMIT = new DefaultRedisScript<>("""
            for _, key in ipairs(KEYS) do
                if redis.call('TTL', key) == -1 then
                    redis.call('EXPIRE', key, ARGV[1])
                end
            end
            for i, key in ipairs(KEYS) do
                if tonumber(redis.call('GET', key) or '0') >= tonumber(ARGV[i + 1]) then
                    return 0
                end
            end
            for _, key in ipairs(KEYS) do
                local count = redis.call('INCR', key)
                if count == 1 then redis.call('EXPIRE', key, ARGV[1]) end
            end
            return 1
            """, Long.class);
    private final StringRedisTemplate redis;
    private final int globalLimit;
    private final int sourceLimit;
    private final boolean directPeerOnly;

    public EmailSendRateLimiter(StringRedisTemplate redis,
            @Value("${BABI_EMAIL_SEND_GLOBAL_PER_MINUTE:30}") int globalLimit,
            @Value("${BABI_EMAIL_SEND_SOURCE_PER_MINUTE:10}") int sourceLimit,
            @Value("${server.forward-headers-strategy:}") String forwardedStrategy) {
        if (globalLimit <= 0 || sourceLimit <= 0) {
            throw new IllegalArgumentException("Email send limits must be positive");
        }
        this.redis = redis;
        this.globalLimit = globalLimit;
        this.sourceLimit = sourceLimit;
        // Servlet remote address may have been rewritten by a forwarding filter or valve.
        this.directPeerOnly = "none".equalsIgnoreCase(forwardedStrategy);
    }

    public void check(HttpServletRequest request) {
        String peer = directPeerOnly ? request.getRemoteAddr() : "shared-proxy";
        if (peer == null || peer.isBlank()) peer = "unknown";
        String source = digest(peer);
        Long result;
        try {
            result = redis.execute(LIMIT, List.of("email:send-limit:global", "email:send-limit:source:" + source),
                    Integer.toString(WINDOW_SECONDS), Integer.toString(globalLimit), Integer.toString(sourceLimit));
        } catch (RuntimeException e) {
            throw new EmailVerificationException(ErrorCode.EMAIL_VERIFICATION_UNAVAILABLE);
        }
        if (Long.valueOf(0).equals(result)) throw new EmailVerificationException(ErrorCode.EMAIL_SEND_RATE_LIMITED);
        if (!Long.valueOf(1).equals(result)) throw new EmailVerificationException(ErrorCode.EMAIL_VERIFICATION_UNAVAILABLE);
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
