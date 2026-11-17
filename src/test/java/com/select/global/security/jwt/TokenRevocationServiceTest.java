package com.select.global.security.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.dao.DataAccessResourceFailureException;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TokenRevocationServiceTest {
    @Test void redisFailureDoesNotAllowARevokedToken() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.hasKey(anyString())).thenReturn(null);
        assertThrows(DataAccessResourceFailureException.class, () -> new TokenRevocationService(redis).isRevoked("sensitive-token"));
    }

    @SuppressWarnings("unchecked")
    @Test void savesHashForRemainingLifetime() {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        new TokenRevocationService(redis).revoke("sensitive-token", Duration.ofMinutes(5));
        verify(values).set(argThat(key -> key.startsWith("auth:revoked:") && !key.contains("sensitive-token")), eq("1"), eq(Duration.ofMinutes(5)));
    }
}
