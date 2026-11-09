package com.select.domain.user.service;

import com.select.domain.user.exception.AccountLifecycleException;
import com.select.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WithdrawalRateLimiterTest {
    @Test void limitsPerUserAndFailsClosedWhenRedisFails() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList())).thenReturn(5L, 6L)
                .thenThrow(new DataAccessResourceFailureException("unavailable"));
        var limiter = new WithdrawalRateLimiter(redis);
        assertDoesNotThrow(() -> limiter.check(42L));
        assertEquals(ErrorCode.ACCOUNT_WITHDRAWAL_RATE_LIMITED,
                assertThrows(AccountLifecycleException.class, () -> limiter.check(42L)).getErrorCode());
        assertEquals(ErrorCode.AUTH_SERVICE_UNAVAILABLE,
                assertThrows(AccountLifecycleException.class, () -> limiter.check(42L)).getErrorCode());
        verify(redis, times(3)).execute(any(RedisScript.class), eq(java.util.List.of("auth:withdraw-attempts:42")));
    }
}
