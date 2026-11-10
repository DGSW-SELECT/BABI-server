package com.select.domain.handover.service;

import com.select.domain.handover.exception.HandoverMemoException;
import com.select.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HandoverMemoRateLimiter {

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
    private final int userLimit;
    private final int babyLimit;

    public HandoverMemoRateLimiter(
            StringRedisTemplate redis,
            @Value("${handover.memo.limits.user-per-minute:20}") int userLimit,
            @Value("${handover.memo.limits.baby-per-minute:60}") int babyLimit
    ) {
        if (userLimit <= 0 || babyLimit <= 0) {
            throw new IllegalArgumentException("인수인계 메모 요청 제한은 0보다 커야 합니다.");
        }
        this.redis = redis;
        this.userLimit = userLimit;
        this.babyLimit = babyLimit;
    }

    public void check(Long userId, Long babyId) {
        Long result;
        try {
            result = redis.execute(
                    LIMIT,
                    List.of("handover:memo:create:user:" + userId, "handover:memo:create:baby:" + babyId),
                    Integer.toString(WINDOW_SECONDS),
                    Integer.toString(userLimit),
                    Integer.toString(babyLimit)
            );
        } catch (RuntimeException e) {
            throw new HandoverMemoException(ErrorCode.HANDOVER_MEMO_UNAVAILABLE);
        }
        if (Long.valueOf(0L).equals(result)) {
            throw new HandoverMemoException(ErrorCode.HANDOVER_MEMO_RATE_LIMITED);
        }
        if (!Long.valueOf(1L).equals(result)) {
            throw new HandoverMemoException(ErrorCode.HANDOVER_MEMO_UNAVAILABLE);
        }
    }
}
