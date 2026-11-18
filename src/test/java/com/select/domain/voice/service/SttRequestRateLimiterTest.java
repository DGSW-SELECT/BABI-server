package com.select.domain.voice.service;

import com.select.domain.voice.exception.SttRequestRateLimitException;
import com.select.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class SttRequestRateLimiterTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final SttRequestRateLimiter rateLimiter = new SttRequestRateLimiter(redis);

    @Test
    void rejectsRequestsWhenRedisReportsTheLimitWasReached() {
        doReturn(0L).when(redis).execute(
                any(RedisScript.class), anyList(), any(Object[].class)
        );

        assertThatThrownBy(() -> rateLimiter.consume(42L))
                .isInstanceOfSatisfying(SttRequestRateLimitException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.STT_RATE_LIMIT_EXCEEDED));
    }

    @Test
    void failsClosedWhenRedisIsUnavailable() {
        doThrow(new IllegalStateException("Redis unavailable")).when(redis).execute(
                any(RedisScript.class), anyList(), any(Object[].class)
        );

        assertThatThrownBy(() -> rateLimiter.consume(42L))
                .isInstanceOfSatisfying(SttRequestRateLimitException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.STT_RATE_LIMITER_UNAVAILABLE));
    }
}
