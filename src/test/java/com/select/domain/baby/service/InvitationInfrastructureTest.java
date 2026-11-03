package com.select.domain.baby.service;

import com.select.domain.baby.exception.BabyInvitationException;
import com.select.domain.baby.entity.BabyEmailInvitation;
import com.select.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.time.Instant;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class InvitationInfrastructureTest {
    @Test
    void generatedCodesUseExactlySixAsciiAlphanumericCharacters() {
        var generator = new InvitationCodeGenerator();
        for (int i = 0; i < 100; i++) assertTrue(generator.generate().matches("[A-Za-z0-9]{6}"));
    }

    @Test
    void invitationExpiresExactlyAtTheFourteenDayBoundary() {
        Instant sent = Instant.parse("2026-10-28T00:00:00Z");
        var invitation = new BabyEmailInvitation(1L, "test@example.invalid", "generation", sent);
        Instant expiry = sent.plus(Duration.ofDays(14));
        assertTrue(invitation.usable("generation", expiry.minusNanos(1)));
        assertFalse(invitation.usable("generation", expiry));
        assertFalse(invitation.usable("different-generation", sent));
    }

    @Test
    void linkContainsOnlyCodeAndUsesConfiguredHttpsLandingRoute() {
        var links = new InvitationLinkFactory("https://app.babi.test/invite");
        assertEquals("https://app.babi.test/invite?code=AbC123", links.create("AbC123"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "http://app.babi.test/invite", "https://user:password@app.babi.test/invite",
            "https://app.babi.test/invite?email=private", "https://app.babi.test/#/invite", "https:///invite"})
    void invalidLandingConfigurationFailsClosed(String url) {
        var error = assertThrows(BabyInvitationException.class, () -> new InvitationLinkFactory(url).create("AbC123"));
        assertEquals(ErrorCode.INVITATION_UNAVAILABLE, error.getErrorCode());
    }

    @Test
    void redisDenialAndOutageFailClosedWithoutProceeding() {
        var redis = mock(StringRedisTemplate.class);
        var limiter = new InvitationRateLimiter(redis, 30, 10, 10, 30, 3, 10);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(0L);
        assertEquals(ErrorCode.INVITATION_RATE_LIMITED,
                assertThrows(BabyInvitationException.class, () -> limiter.lookup(1L)).getErrorCode());
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(null);
        assertEquals(ErrorCode.INVITATION_UNAVAILABLE,
                assertThrows(BabyInvitationException.class, () -> limiter.accept(1L)).getErrorCode());
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("test outage"));
        var failure = assertThrows(BabyInvitationException.class, () -> limiter.rotate(1L));
        assertEquals(ErrorCode.INVITATION_UNAVAILABLE, failure.getErrorCode());
        assertNull(failure.getCause());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void sendChecksAllThreeBucketsAtomicallyAndKeepsRawEmailOutOfRedisKeys() {
        var redis = mock(StringRedisTemplate.class);
        var limiter = new InvitationRateLimiter(redis, 30, 10, 10, 30, 3, 10);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);
        limiter.send(1L, 2L, "private@example.invalid");
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redis).execute(any(RedisScript.class), keys.capture(), eq("3600"), eq("10"), eq("30"), eq("3"));
        assertEquals(3, keys.getValue().size());
        assertTrue(keys.getValue().stream().allMatch(key -> key.startsWith("baby:invite:")));
        assertTrue(keys.getValue().stream().noneMatch(key -> key.contains("private") || key.contains("@")));
    }

    @Test
    void invalidQuotaConfigurationIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new InvitationRateLimiter(mock(StringRedisTemplate.class), 0, 10, 10, 30, 3, 10));
    }
}
