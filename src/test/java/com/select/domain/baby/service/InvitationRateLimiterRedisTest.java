package com.select.domain.baby.service;

import com.select.domain.baby.exception.BabyInvitationException;
import com.select.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "BABI_REDIS_TEST_PORT", matches = "[0-9]+")
class InvitationRateLimiterRedisTest {
    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private final List<String> keys = new ArrayList<>();
    private long id;

    @BeforeEach void setUp() {
        factory = new LettuceConnectionFactory("127.0.0.1", Integer.parseInt(System.getenv("BABI_REDIS_TEST_PORT")));
        factory.afterPropertiesSet();
        factory.start();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        id = ThreadLocalRandom.current().nextLong(1_000_000_000L, 8_000_000_000L);
    }
    @AfterEach void tearDown() {
        if (!keys.isEmpty()) redis.delete(keys);
        factory.destroy();
    }
    private void send(InvitationRateLimiter limiter, long user, long baby, String email) throws Exception {
        keys.add("baby:invite:send:user:" + user);
        keys.add("baby:invite:send:baby:" + baby);
        keys.add("baby:invite:send:target:" + baby + ":" + HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(email.getBytes(StandardCharsets.UTF_8))));
        limiter.send(user, baby, email);
    }
    private void rejected(InvitationRateLimiter limiter, long user, long baby, String email) {
        var error = assertThrows(BabyInvitationException.class, () -> send(limiter, user, baby, email));
        assertEquals(ErrorCode.INVITATION_RATE_LIMITED, error.getErrorCode());
    }
    @Test void userLimitSpansBabiesAndDestinations() throws Exception {
        var limiter = new InvitationRateLimiter(redis, 30, 10, 2, 30, 3, 10);
        send(limiter, id, id + 10, "one@example.invalid");
        send(limiter, id, id + 11, "two@example.invalid");
        rejected(limiter, id, id + 12, "three@example.invalid");
        assertTrue(redis.getExpire("baby:invite:send:user:" + id) > 0);
    }
    @Test void babyLimitSpansOwnersAndDestinations() throws Exception {
        var limiter = new InvitationRateLimiter(redis, 30, 10, 10, 2, 3, 10);
        send(limiter, id, id + 10, "one@example.invalid");
        send(limiter, id + 1, id + 10, "two@example.invalid");
        rejected(limiter, id + 2, id + 10, "three@example.invalid");
    }
    @Test void targetLimitIsSpecificToBabyAndEmail() throws Exception {
        var limiter = new InvitationRateLimiter(redis, 30, 10, 10, 30, 2, 10);
        send(limiter, id, id + 10, "one@example.invalid");
        send(limiter, id, id + 10, "one@example.invalid");
        rejected(limiter, id, id + 10, "one@example.invalid");
        send(limiter, id, id + 10, "other@example.invalid");
        assertTrue(keys.stream().noneMatch(key -> key.contains("@")));
    }
}
