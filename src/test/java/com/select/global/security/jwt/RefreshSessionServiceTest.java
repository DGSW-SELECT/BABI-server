package com.select.global.security.jwt;

import com.select.domain.user.exception.AccountLifecycleException;
import com.select.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RefreshSessionServiceTest {
    @Test void redisFailureDoesNotAcceptRefresh() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("unavailable"));
        var service = new RefreshSessionService(redis);
        String token = "a".repeat(43) + "." + "b".repeat(43);
        var exception = assertThrows(AccountLifecycleException.class, () -> service.rotate(token));
        assertEquals(ErrorCode.AUTH_SERVICE_UNAVAILABLE, exception.getErrorCode());
    }
    @Test void redisFailureDoesNotPretendLogoutSucceeded() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any()))
                .thenThrow(new DataAccessResourceFailureException("unavailable"));
        String token = "a".repeat(43) + "." + "b".repeat(43);
        var exception = assertThrows(AccountLifecycleException.class, () -> new RefreshSessionService(redis).revoke(token));
        assertEquals(ErrorCode.AUTH_SERVICE_UNAVAILABLE, exception.getErrorCode());
    }
    @Test void malformedRefreshIsRejectedBeforeRedis() {
        var redis = mock(StringRedisTemplate.class);
        var exception = assertThrows(AccountLifecycleException.class, () -> new RefreshSessionService(redis).rotate("bad"));
        assertEquals(ErrorCode.INVALID_TOKEN, exception.getErrorCode());
        verifyNoInteractions(redis);
    }
}
