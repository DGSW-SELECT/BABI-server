package com.select.domain.user.service;

import com.select.domain.user.exception.AccountLifecycleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "BABI_REDIS_TEST_PORT", matches = "[0-9]+")
class WithdrawalRateLimiterRedisTest {
    @Test void concurrentRequestsRespectLimitAndExpire() throws Exception {
        var factory = new LettuceConnectionFactory("127.0.0.1", Integer.parseInt(System.getenv("BABI_REDIS_TEST_PORT")));
        factory.afterPropertiesSet();
        factory.start();
        var redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        long userId = System.nanoTime();
        String key = "auth:withdraw-attempts:" + userId;
        try (var pool = Executors.newFixedThreadPool(12)) {
            var limiter = new WithdrawalRateLimiter(redis);
            var start = new CountDownLatch(1);
            var tasks = IntStream.range(0, 12).mapToObj(i -> pool.submit(() -> {
                start.await();
                try { limiter.check(userId); return true; }
                catch (AccountLifecycleException ex) { return false; }
            })).toList();
            start.countDown();
            int allowed = 0;
            for (var task : tasks) if (task.get()) allowed++;
            assertEquals(5, allowed);
            assertTrue(redis.getExpire(key) > 0);
        } finally {
            redis.delete(key);
            factory.destroy();
        }
    }
}
