package com.select.domain.auth.service;

import com.select.domain.auth.exception.EmailVerificationException;
import com.select.global.exception.ErrorCode;
import com.select.global.exception.GlobalExceptionHandler;
import com.select.domain.auth.controller.EmailVerificationController;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "BABI_REDIS_TEST_PORT", matches = "[0-9]+")
class EmailSendRateLimiterRedisTest {
    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private static final String GLOBAL = "email:send-limit:global";
    private static final List<String> PEERS = List.of("127.0.0.1", "192.0.2.1", "192.0.2.2", "shared-proxy");

    @BeforeEach void start() {
        factory = new LettuceConnectionFactory("127.0.0.1", Integer.parseInt(System.getenv("BABI_REDIS_TEST_PORT")));
        factory.afterPropertiesSet(); factory.start();
        redis = new StringRedisTemplate(factory); redis.afterPropertiesSet();
        clear();
    }
    @AfterEach void stop() { clear(); factory.destroy(); }
    private void clear() {
        redis.delete(GLOBAL);
        for (String peer : PEERS) redis.delete(sourceKey(peer));
    }
    private String sourceKey(String peer) {
        try { return "email:send-limit:source:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(peer.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new AssertionError(ex); }
    }
    private MockHttpServletRequest request(String peer, String forwarded) {
        var request = new MockHttpServletRequest(); request.setRemoteAddr(peer);
        if (forwarded != null) request.addHeader("X-Forwarded-For", forwarded);
        return request;
    }
    private void blocked(EmailSendRateLimiter limiter, MockHttpServletRequest request) {
        assertEquals(ErrorCode.EMAIL_SEND_RATE_LIMITED, assertThrows(EmailVerificationException.class, () -> limiter.check(request)).getErrorCode());
    }
    @Test void sourceLimitIgnoresSpoofedForwardedHeaders() {
        var limiter = new EmailSendRateLimiter(redis, 20, 2, "none");
        limiter.check(request("192.0.2.1", "1.1.1.1"));
        limiter.check(request("192.0.2.1", "2.2.2.2"));
        blocked(limiter, request("192.0.2.1", "3.3.3.3"));
        limiter.check(request("192.0.2.2", null));
        assertTrue(redis.getExpire(GLOBAL) > 0);
        assertTrue(redis.getExpire(sourceKey("192.0.2.1")) > 0);
    }
    @Test void globalLimitAppliesAcrossDifferentSources() {
        var limiter = new EmailSendRateLimiter(redis, 2, 5, "none");
        limiter.check(request("192.0.2.1", null)); limiter.check(request("192.0.2.2", null));
        blocked(limiter, request("127.0.0.1", null));
    }
    @Test void globalLimitBlocksHttpSendAcrossDifferentSources() throws Exception {
        var service = mock(EmailVerificationService.class);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new EmailVerificationController(service, new EmailSendRateLimiter(redis, 2, 10, "none")))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        httpSend(mvc, "192.0.2.1", "one@example.invalid", 200);
        httpSend(mvc, "192.0.2.2", "two@example.invalid", 200);
        httpSend(mvc, "127.0.0.1", "three@example.invalid", 429);
        verify(service).sendVerificationCode("one@example.invalid");
        verify(service).sendVerificationCode("two@example.invalid");
        verify(service, times(3)).validateRecipient(anyString());
        verifyNoMoreInteractions(service);
    }
    @Test void sourceLimitBlocksHttpSendWithoutBlockingOtherSources() throws Exception {
        var service = mock(EmailVerificationService.class);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new EmailVerificationController(service, new EmailSendRateLimiter(redis, 20, 2, "none")))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        httpSend(mvc, "192.0.2.1", "one@example.invalid", 200);
        httpSend(mvc, "192.0.2.1", "two@example.invalid", 200);
        httpSend(mvc, "192.0.2.1", "three@example.invalid", 429);
        httpSend(mvc, "192.0.2.2", "four@example.invalid", 200);
        verify(service, times(3)).sendVerificationCode(anyString());
        verify(service, never()).sendVerificationCode("three@example.invalid");
    }
    private void httpSend(org.springframework.test.web.servlet.MockMvc mvc, String peer, String email, int expected) throws Exception {
        var result = mvc.perform(post("/auth/email/send").contentType("application/json")
                .content("{\"email\":\"" + email + "\"}").with(req -> { req.setRemoteAddr(peer); return req; }))
                .andExpect(status().is(expected));
        if (expected == 429) result.andExpect(jsonPath("$.error.code").value("EMAIL_SEND_RATE_LIMITED"));
    }
    @Test void forwardedStrategyUsesSharedBucket() {
        var limiter = new EmailSendRateLimiter(redis, 20, 1, "native");
        limiter.check(request("192.0.2.1", "1.1.1.1"));
        blocked(limiter, request("192.0.2.2", "2.2.2.2"));
    }
    @Test void allowsRequestsAfterExpiration() throws Exception {
        var limiter = new EmailSendRateLimiter(redis, 1, 1, "none");
        var request = request("192.0.2.1", null); limiter.check(request); blocked(limiter, request);
        redis.expire(GLOBAL, Duration.ofMillis(50)); redis.expire(sourceKey("192.0.2.1"), Duration.ofMillis(50));
        Thread.sleep(120); assertDoesNotThrow(() -> limiter.check(request));
    }
    @Test void repairsMissingTtlEvenWhenLimitAlreadyExceeded() {
        var limiter = new EmailSendRateLimiter(redis, 1, 1, "none");
        redis.opsForValue().set(GLOBAL, "1");
        redis.opsForValue().set(sourceKey("192.0.2.1"), "1");
        blocked(limiter, request("192.0.2.1", null));
        assertTrue(redis.getExpire(GLOBAL) > 0);
        assertTrue(redis.getExpire(sourceKey("192.0.2.1")) > 0);
    }
    @Test void concurrentRequestsCannotExceedLimit() throws Exception {
        var limiter = new EmailSendRateLimiter(redis, 7, 7, "none");
        var pool = Executors.newFixedThreadPool(20); var start = new CountDownLatch(1); var ok = new AtomicInteger();
        try {
            var tasks = new java.util.ArrayList<Future<?>>();
            for (int i = 0; i < 20; i++) tasks.add(pool.submit(() -> {
                try { start.await(); limiter.check(request("192.0.2.1", null)); ok.incrementAndGet(); }
                catch (EmailVerificationException ex) { assertEquals(ErrorCode.EMAIL_SEND_RATE_LIMITED, ex.getErrorCode()); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new AssertionError(ex); }
            }));
            start.countDown(); for (var task : tasks) task.get(10, TimeUnit.SECONDS);
            assertEquals(7, ok.get()); assertEquals("7", redis.opsForValue().get(GLOBAL));
        } finally { pool.shutdownNow(); }
    }
}
