package com.select.domain.auth.mail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class DiscordWebhookEmailSenderTest {
    private static final String WEBHOOK = "https://discord.com/api/webhooks/123/test_secret";
    private static final String BODY = "BABI 이메일 인증번호는 123456입니다.\n인증번호는 5분 동안 유효합니다.";
    @SuppressWarnings("unchecked")
    @Test void sendsToMockAndRejectsNonTestAddress() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<java.io.InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(204);
        AtomicReference<HttpRequest> sent = new AtomicReference<>();
        when(client.send(any(), any(HttpResponse.BodyHandler.class))).thenAnswer(inv -> {sent.set(inv.getArgument(0)); return response;});
        var sender = new DiscordWebhookEmailSender(WEBHOOK, new ObjectMapper(), client);
        sender.send("test@example.invalid", "[BABI] 이메일 인증번호 안내", BODY);
        assertNotNull(sent.get());
        assertEquals("POST", sent.get().method());
        String payload = requestBody(sent.get());
        assertTrue(payload.contains("123456"));
        var json = new ObjectMapper().readTree(payload);
        assertTrue(json.path("allowed_mentions").path("parse").isArray());
        assertEquals(0, json.path("allowed_mentions").path("parse").size());
        assertEquals(java.time.Duration.ofSeconds(5), sent.get().timeout().orElseThrow());
        assertFalse(payload.contains("test@example.invalid"));
        assertThrows(com.select.domain.auth.exception.EmailVerificationException.class, () -> sender.send("real@example.com", "[BABI] 이메일 인증번호 안내", BODY));
        verify(client, times(1)).send(any(), any(HttpResponse.BodyHandler.class));
    }
    @SuppressWarnings("unchecked")
    @Test void invitationDoesNotExposeLinkOrAddress() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<java.io.InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(204);
        AtomicReference<HttpRequest> sent = new AtomicReference<>();
        when(client.send(any(), any(HttpResponse.BodyHandler.class))).thenAnswer(inv -> { sent.set(inv.getArgument(0)); return response; });
        var sender = new DiscordWebhookEmailSender(WEBHOOK, new ObjectMapper(), client);
        sender.send("demo@example.invalid", "[BABI] 아기 보호자 초대", "BABI 아기 보호자로 초대되었습니다.\n초대 코드: ABC123\n초대 링크: https://demo.example.invalid/invite?code=ABC123\n이메일 초대 유효기간: 2026-01-01T00:00:00Z\n로그인한 후 초대 코드를 입력해 주세요.");
        String payload = requestBody(sent.get());
        assertTrue(payload.contains("ABC123"));
        assertFalse(payload.contains("https://demo"));
        assertFalse(payload.contains("demo@example.invalid"));
    }

    @SuppressWarnings("unchecked")
    @Test void verificationServiceSendsGeneratedCodeAndKeepsVerificationContract() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<java.io.InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(204);
        AtomicReference<HttpRequest> sent = new AtomicReference<>();
        when(client.send(any(), any(HttpResponse.BodyHandler.class))).thenAnswer(inv -> { sent.set(inv.getArgument(0)); return response; });
        var redis = mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        var values = mock(org.springframework.data.redis.core.ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(java.time.Duration.class))).thenReturn(true);
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class), anyList(), anyString(), anyString(), anyString())).thenReturn(1L);
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(1L);
        var sender = new DiscordWebhookEmailSender(WEBHOOK, new ObjectMapper(), client);
        var service = new com.select.domain.auth.service.EmailVerificationService(sender, redis, "test-only-secret");
        service.sendVerificationCode("demo@example.invalid");
        var code = java.util.regex.Pattern.compile("인증번호: ([0-9]{6})").matcher(requestBody(sent.get()));
        assertTrue(code.find());
        var verified = service.verifyCode("demo@example.invalid", code.group(1));
        assertTrue(verified.verified());
        assertEquals(43, verified.verificationToken().length());
    }

    @SuppressWarnings("unchecked")
    @Test void failureAnd429DoNotExposeUrl(CapturedOutput output) throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<java.io.InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(429, 500);
        when(client.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var sender = new DiscordWebhookEmailSender(WEBHOOK, new ObjectMapper(), client);
        for (int i=0;i<2;i++) {
            var ex = assertThrows(IllegalStateException.class, () -> sender.send("test@example.invalid", "[BABI] 이메일 인증번호 안내", BODY));
            assertEquals(i == 0 ? "Discord rate limited" : "Discord delivery failed", ex.getMessage());
            assertFalse(ex.toString().contains("test_secret"));
            assertNull(ex.getCause());
        }
        assertFalse(output.getAll().contains("test_secret"));
    }
    @SuppressWarnings("unchecked")
    @Test void timeoutDoesNotExposeUrl() throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(), any(HttpResponse.BodyHandler.class))).thenThrow(new HttpTimeoutException(WEBHOOK));
        var sender = new DiscordWebhookEmailSender(WEBHOOK, new ObjectMapper(), client);
        var ex = assertThrows(IllegalStateException.class, () -> sender.send("test@example.invalid", "[BABI] 이메일 인증번호 안내", BODY));
        assertFalse(ex.toString().contains("test_secret"));
        assertNull(ex.getCause());
    }
    @Test void sendsActualHttpRequestOnlyToLoopbackMock() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        var payload = new AtomicReference<String>();
        server.createContext("/webhook", exchange -> {
            payload.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            var uri = java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/webhook");
            var sender = new DiscordWebhookEmailSender(uri, new ObjectMapper(), HttpClient.newHttpClient());
            sender.send("test@example.invalid", "[BABI] 이메일 인증번호 안내", BODY);
            assertTrue(payload.get().contains("123456"));
            assertFalse(payload.get().contains("test@example.invalid"));
        } finally { server.stop(0); }
    }
    @SuppressWarnings("unchecked")
    @Test void differentConfiguredUrlsAreUsedWithoutCodeChanges() throws Exception {
        var client = mock(HttpClient.class);
        HttpResponse<java.io.InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(204);
        var targets = new java.util.ArrayList<java.net.URI>();
        when(client.send(any(), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            HttpRequest request = call.getArgument(0);
            targets.add(request.uri());
            return response;
        });
        for (String endpoint : java.util.List.of("https://discord.com/api/webhooks/100/first_test_token",
                "https://discord.com/api/webhooks/200/second_test_token")) {
            new DiscordWebhookEmailSender(endpoint, new ObjectMapper(), client)
                    .send("test@example.invalid", "[BABI] 이메일 인증번호 안내", BODY);
        }
        assertEquals(2, targets.size());
        assertEquals("/api/webhooks/100/first_test_token", targets.get(0).getPath());
        assertEquals("/api/webhooks/200/second_test_token", targets.get(1).getPath());
    }

    @Test void productionClientHasConnectionTimeoutAndNoRedirects() {
        var client = DiscordWebhookEmailSender.createClient();
        assertEquals(java.time.Duration.ofSeconds(3), client.connectTimeout().orElseThrow());
        assertEquals(HttpClient.Redirect.NEVER, client.followRedirects());
    }

    @Test void stalledResponseBodyDoesNotBlockDelivery() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        var headersSent = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        server.createContext("/webhook", exchange -> {
            try {
                exchange.sendResponseHeaders(200, 0);
                headersSent.countDown();
                release.await(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var uri = java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/webhook");
            var sender = new DiscordWebhookEmailSender(uri, new ObjectMapper(), DiscordWebhookEmailSender.createClient());
            var task = executor.submit(() -> sender.send("test@example.invalid", "[BABI] 이메일 인증번호 안내", BODY));
            assertTrue(headersSent.await(3, java.util.concurrent.TimeUnit.SECONDS));
            task.get(3, java.util.concurrent.TimeUnit.SECONDS);
        } finally { release.countDown(); server.stop(0); }
    }

    @Test void stalledResponseHeadersTimeOutWithoutExposingUrl() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        var release = new java.util.concurrent.CountDownLatch(1);
        server.createContext("/webhook", exchange -> {
            try { release.await(10, java.util.concurrent.TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            var uri = java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/webhook");
            var sender = new DiscordWebhookEmailSender(uri, new ObjectMapper(), DiscordWebhookEmailSender.createClient());
            long started = System.nanoTime();
            var error = assertThrows(IllegalStateException.class,
                    () -> sender.send("test@example.invalid", "[BABI] 이메일 인증번호 안내", BODY));
            long elapsed = java.time.Duration.ofNanos(System.nanoTime() - started).toMillis();
            assertTrue(elapsed >= 4000 && elapsed < 10000, "elapsed=" + elapsed);
            assertFalse(error.toString().contains(uri.toString()));
            assertNull(error.getCause());
        } finally { release.countDown(); server.stop(0); }
    }

    @Test void rejectsUnknownUrlsAndMessages() {
        assertThrows(IllegalArgumentException.class, () -> new DiscordWebhookEmailSender("https://invalid.example/webhook", new ObjectMapper(), mock(HttpClient.class)));
        var sender = new DiscordWebhookEmailSender(WEBHOOK, new ObjectMapper(), mock(HttpClient.class));
        assertThrows(IllegalArgumentException.class, () -> sender.send("test@example.invalid", "unknown", "personal info"));
    }
    private static String requestBody(HttpRequest request) throws Exception {
        var result = new java.util.concurrent.CompletableFuture<String>();
        var bytes = new java.io.ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer>() {
            public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(java.nio.ByteBuffer item) { byte[] b = new byte[item.remaining()]; item.get(b); bytes.writeBytes(b); }
            public void onError(Throwable error) { result.completeExceptionally(error); }
            public void onComplete() { result.complete(bytes.toString(java.nio.charset.StandardCharsets.UTF_8)); }
        });
        return result.get(3, java.util.concurrent.TimeUnit.SECONDS);
    }

}
