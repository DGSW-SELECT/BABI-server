package com.select.domain.user.service;

import com.select.domain.user.exception.AccountLifecycleException;
import com.select.global.exception.ErrorCode;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/** Limits password confirmation attempts across sessions and application instances. */
@Component
public class WithdrawalRateLimiter {
    private static final DefaultRedisScript<Long> CHECK = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 or redis.call('TTL', KEYS[1]) == -1 then
                redis.call('EXPIRE', KEYS[1], 900)
            end
            return count
            """, Long.class);
    private final StringRedisTemplate redis;

    public WithdrawalRateLimiter(StringRedisTemplate redis) { this.redis = redis; }

    public void check(Long userId) {
        Long count;
        try { count = redis.execute(CHECK, List.of("auth:withdraw-attempts:" + userId)); }
        catch (DataAccessException e) { throw new AccountLifecycleException(ErrorCode.AUTH_SERVICE_UNAVAILABLE); }
        if (count == null || count < 1) throw new AccountLifecycleException(ErrorCode.AUTH_SERVICE_UNAVAILABLE);
        if (count > 5) throw new AccountLifecycleException(ErrorCode.ACCOUNT_WITHDRAWAL_RATE_LIMITED);
    }
}
