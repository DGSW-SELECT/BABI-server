package com.select.domain.auth.service;

import com.select.domain.auth.exception.EmailVerificationException;
import com.select.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailSendRateLimiterTest {
    @Test void invalidLimitsFailAtStartup() {
        var redis = mock(StringRedisTemplate.class);
        assertThrows(IllegalArgumentException.class, () -> new EmailSendRateLimiter(redis, 0, 1, "none"));
        assertThrows(IllegalArgumentException.class, () -> new EmailSendRateLimiter(redis, 1, -1, "none"));
    }

    @Test void nonNumericEnvironmentFailsBeanCreation() {
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("invalid",
                    java.util.Map.of("BABI_EMAIL_SEND_GLOBAL_PER_MINUTE", "not-a-number")));
            context.registerBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class));
            context.register(EmailSendRateLimiter.class);
            assertThrows(RuntimeException.class, context::refresh);
        }
    }

    @Test void unavailableRedisRejectsRequests() throws Exception {
        int port;
        try (var socket = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            port = socket.getLocalPort();
        }
        var factory = new org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory("127.0.0.1", port);
        factory.afterPropertiesSet(); factory.start();
        try {
            var redis = new StringRedisTemplate(factory); redis.afterPropertiesSet();
            var limiter = new EmailSendRateLimiter(redis, 30, 10, "none");
            assertEquals(ErrorCode.EMAIL_VERIFICATION_UNAVAILABLE,
                    assertThrows(EmailVerificationException.class, () -> limiter.check(new MockHttpServletRequest())).getErrorCode());
        } finally { factory.destroy(); }
    }

    @SuppressWarnings("unchecked")
    @Test void redisFailureOrUnexpectedResultFailsClosed() {
        var redis = mock(StringRedisTemplate.class);
        var limiter = new EmailSendRateLimiter(redis, 30, 10, "none");
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any())).thenThrow(new RuntimeException("secret"));
        var ex = assertThrows(EmailVerificationException.class, () -> limiter.check(new MockHttpServletRequest()));
        assertEquals(ErrorCode.EMAIL_VERIFICATION_UNAVAILABLE, ex.getErrorCode());
        assertFalse(ex.toString().contains("secret"));
    }
}
