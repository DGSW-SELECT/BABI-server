package com.select.domain.voice.service;

import com.select.domain.voice.exception.SttRequestRateLimitException;
import com.select.global.exception.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** 동기·비동기 STT 요청의 사용자별 유료 호출 횟수를 제한한다. */
@Component
public class SttRequestRateLimiter {

    private static final int REQUESTS_PER_USER = 20;
    private static final long WINDOW_MILLIS = Duration.ofHours(24).toMillis();
    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)
            if redis.call('ZCARD', key) >= tonumber(ARGV[3]) then
                return 0
            end
            redis.call('ZADD', key, now, ARGV[4])
            redis.call('PEXPIRE', key, window)
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;

    public SttRequestRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void consume(Long userId) {
        Long result;
        try {
            result = redis.execute(
                    CONSUME,
                    List.of("voice:stt:requests:" + userId),
                    Long.toString(System.currentTimeMillis()),
                    Long.toString(WINDOW_MILLIS),
                    Integer.toString(REQUESTS_PER_USER),
                    UUID.randomUUID().toString()
            );
        } catch (RuntimeException e) {
            throw new SttRequestRateLimitException(ErrorCode.STT_RATE_LIMITER_UNAVAILABLE);
        }

        if (Long.valueOf(0L).equals(result)) {
            throw new SttRequestRateLimitException(ErrorCode.STT_RATE_LIMIT_EXCEEDED);
        }
        if (!Long.valueOf(1L).equals(result)) {
            throw new SttRequestRateLimitException(ErrorCode.STT_RATE_LIMITER_UNAVAILABLE);
        }
    }
}
