package com.select.domain.handover;

import com.select.domain.handover.exception.HandoverMemoException;
import com.select.domain.handover.service.HandoverMemoRateLimiter;
import com.select.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "BABI_REDIS_TEST_PORT", matches = "[0-9]+")
class HandoverMemoRateLimiterRedisTest {

    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private final List<String> keys = new ArrayList<>();
    private long id;

    @BeforeEach
    void setUp() {
        factory = new LettuceConnectionFactory(
                "127.0.0.1", Integer.parseInt(System.getenv("BABI_REDIS_TEST_PORT")));
        factory.afterPropertiesSet();
        factory.start();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        id = ThreadLocalRandom.current().nextLong(1_000_000_000L, 8_000_000_000L);
    }

    @AfterEach
    void tearDown() {
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
        factory.destroy();
    }

    @Test
    void userLimitSpansBabiesAndSetsTtl() {
        HandoverMemoRateLimiter limiter = new HandoverMemoRateLimiter(redis, 2, 10);

        check(limiter, id, id + 10);
        check(limiter, id, id + 11);
        assertRateLimited(() -> check(limiter, id, id + 12));

        assertTrue(redis.getExpire(userKey(id)) > 0);
    }

    @Test
    void babyLimitSpansUsers() {
        HandoverMemoRateLimiter limiter = new HandoverMemoRateLimiter(redis, 10, 2);
        long babyId = id + 10;

        check(limiter, id, babyId);
        check(limiter, id + 1, babyId);
        assertRateLimited(() -> check(limiter, id + 2, babyId));
    }

    @Test
    void existingCounterWithoutTtlIsRepairedBeforeRejection() {
        HandoverMemoRateLimiter limiter = new HandoverMemoRateLimiter(redis, 1, 10);
        String userKey = userKey(id);
        keys.add(userKey);
        redis.opsForValue().set(userKey, "1");

        assertRateLimited(() -> check(limiter, id, id + 10));

        assertTrue(redis.getExpire(userKey) > 0);
    }

    private void check(HandoverMemoRateLimiter limiter, long userId, long babyId) {
        keys.add(userKey(userId));
        keys.add(babyKey(babyId));
        limiter.check(userId, babyId);
    }

    private void assertRateLimited(Runnable request) {
        HandoverMemoException error = assertThrows(HandoverMemoException.class, request::run);
        assertEquals(ErrorCode.HANDOVER_MEMO_RATE_LIMITED, error.getErrorCode());
    }

    private String userKey(long userId) {
        return "handover:memo:create:user:" + userId;
    }

    private String babyKey(long babyId) {
        return "handover:memo:create:baby:" + babyId;
    }
}
