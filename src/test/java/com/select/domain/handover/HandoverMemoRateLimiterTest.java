package com.select.domain.handover;

import com.select.domain.handover.exception.HandoverMemoException;
import com.select.domain.handover.service.HandoverMemoRateLimiter;
import com.select.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HandoverMemoRateLimiterTest {

    @Test
    void limitsUserAndBabyAtomicallyAndFailsClosed() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HandoverMemoRateLimiter limiter = new HandoverMemoRateLimiter(redis, 20, 60);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L, 0L, null)
                .thenThrow(new DataAccessResourceFailureException("test outage"));

        assertDoesNotThrow(() -> limiter.check(1L, 2L));
        assertEquals(ErrorCode.HANDOVER_MEMO_RATE_LIMITED,
                assertThrows(HandoverMemoException.class, () -> limiter.check(1L, 2L)).getErrorCode());
        assertEquals(ErrorCode.HANDOVER_MEMO_UNAVAILABLE,
                assertThrows(HandoverMemoException.class, () -> limiter.check(1L, 2L)).getErrorCode());
        assertEquals(ErrorCode.HANDOVER_MEMO_UNAVAILABLE,
                assertThrows(HandoverMemoException.class, () -> limiter.check(1L, 2L)).getErrorCode());

        verify(redis, times(4)).execute(
                any(RedisScript.class),
                eq(List.of("handover:memo:create:user:1", "handover:memo:create:baby:2")),
                eq("60"), eq("20"), eq("60")
        );
    }

    @Test
    void rejectsInvalidLimits() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        assertThrows(IllegalArgumentException.class, () -> new HandoverMemoRateLimiter(redis, 0, 60));
        assertThrows(IllegalArgumentException.class, () -> new HandoverMemoRateLimiter(redis, 20, -1));
    }
}
