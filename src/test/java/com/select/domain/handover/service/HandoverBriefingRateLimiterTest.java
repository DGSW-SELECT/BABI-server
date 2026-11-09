package com.select.domain.handover.service;

import com.select.domain.handover.exception.HandoverBriefingRateLimitedException;
import com.select.domain.handover.exception.HandoverBriefingUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HandoverBriefingRateLimiterTest {

    private ValueOperations<String, String> values;
    private HandoverBriefingRateLimiter limiter;
    private static final String KEY = "handover:generate:cooldown:2";
    private static final Duration COOLDOWN = Duration.ofSeconds(60);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        limiter = new HandoverBriefingRateLimiter(redis, 60);
    }

    @Test
    void rejectsRepeatAndAllowsAfterReservationExpires() {
        when(values.setIfAbsent(KEY, "1", COOLDOWN)).thenReturn(true, false, true);
        assertDoesNotThrow(() -> limiter.check(2L));
        assertEquals(60, assertThrows(HandoverBriefingRateLimitedException.class,
                () -> limiter.check(2L)).getRetryAfterSeconds());
        assertDoesNotThrow(() -> limiter.check(2L));
        verify(values, times(3)).setIfAbsent(KEY, "1", COOLDOWN);
    }

    @Test
    void differentBabiesUseSeparateReservations() {
        when(values.setIfAbsent(KEY, "1", COOLDOWN)).thenReturn(false);
        when(values.setIfAbsent("handover:generate:cooldown:3", "1", COOLDOWN)).thenReturn(true);
        assertThrows(HandoverBriefingRateLimitedException.class, () -> limiter.check(2L));
        assertDoesNotThrow(() -> limiter.check(3L));
    }

    @Test
    void redisFailureAndMissingResultFailClosed() {
        when(values.setIfAbsent(KEY, "1", COOLDOWN))
                .thenThrow(new IllegalStateException("redis unavailable"))
                .thenReturn(null);
        assertThrows(HandoverBriefingUnavailableException.class, () -> limiter.check(2L));
        assertThrows(HandoverBriefingUnavailableException.class, () -> limiter.check(2L));
    }

    @Test
    void nonPositiveCooldownIsRejected() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        assertThrows(IllegalArgumentException.class, () -> new HandoverBriefingRateLimiter(redis, 0));
        assertThrows(IllegalArgumentException.class, () -> new HandoverBriefingRateLimiter(redis, -1));
    }
}
