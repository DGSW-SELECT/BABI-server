package com.select.domain.baby.service;

import com.select.domain.baby.exception.BabyInvitationException;
import com.select.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Component
public class InvitationRateLimiter {
    private static final DefaultRedisScript<Long> LIMIT = new DefaultRedisScript<>("""
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
    private final int lookupLimit;
    private final int acceptLimit;
    private final int sendLimit;
    private final int babySendLimit;
    private final int targetSendLimit;
    private final int rotateLimit;

    public InvitationRateLimiter(StringRedisTemplate redis,
            @Value("${baby.invitation.limits.lookup-per-minute:30}") int lookupLimit,
            @Value("${baby.invitation.limits.accept-per-minute:10}") int acceptLimit,
            @Value("${baby.invitation.limits.send-per-hour:10}") int sendLimit,
            @Value("${baby.invitation.limits.baby-send-per-hour:30}") int babySendLimit,
            @Value("${baby.invitation.limits.target-send-per-hour:3}") int targetSendLimit,
            @Value("${baby.invitation.limits.rotate-per-hour:10}") int rotateLimit) {
        this.redis = redis;
        this.lookupLimit = lookupLimit;
        this.acceptLimit = acceptLimit;
        this.sendLimit = sendLimit;
        this.babySendLimit = babySendLimit;
        this.targetSendLimit = targetSendLimit;
        this.rotateLimit = rotateLimit;
        if (List.of(lookupLimit, acceptLimit, sendLimit, babySendLimit, targetSendLimit, rotateLimit)
                .stream().anyMatch(limit -> limit <= 0)) {
            throw new IllegalArgumentException("초대 요청 제한은 0보다 커야 합니다.");
        }
    }

    public void lookup(Long userId) {
        consume(List.of("baby:invite:lookup:" + userId), 60, lookupLimit);
    }

    public void accept(Long userId) {
        consume(List.of("baby:invite:accept:" + userId), 60, acceptLimit);
    }

    public void rotate(Long userId) {
        consume(List.of("baby:invite:rotate:" + userId), 3600, rotateLimit);
    }

    public void send(Long userId, Long babyId, String email) {
        consume(List.of("baby:invite:send:user:" + userId, "baby:invite:send:baby:" + babyId,
                "baby:invite:send:target:" + babyId + ":" + digest(email)),
                3600, sendLimit, babySendLimit, targetSendLimit);
    }

    private void consume(List<String> keys, int seconds, Integer... limits) {
        String[] args = new String[limits.length + 1];
        args[0] = Integer.toString(seconds);
        for (int i = 0; i < limits.length; i++) args[i + 1] = limits[i].toString();
        Long result;
        try {
            result = redis.execute(LIMIT, keys, (Object[]) args);
        } catch (RuntimeException e) {
            throw new BabyInvitationException(ErrorCode.INVITATION_UNAVAILABLE);
        }
        if (Long.valueOf(0L).equals(result)) {
            throw new BabyInvitationException(ErrorCode.INVITATION_RATE_LIMITED);
        }
        if (!Long.valueOf(1L).equals(result)) {
            throw new BabyInvitationException(ErrorCode.INVITATION_UNAVAILABLE);
        }
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
    }
}
