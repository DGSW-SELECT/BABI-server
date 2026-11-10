package com.select.domain.report.service;

import com.select.domain.report.dto.response.DailyReportFacts;
import com.select.domain.report.exception.DailyReportGenerationException;
import com.select.global.exception.ErrorCode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiDailyReportGeneratorTest {
    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private HttpServer server;
    private OpenAiDailyReportGenerator generator;
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> response = new AtomicReference<>("""
            {"status":"completed","output":[{"type":"reasoning","summary":[]},
              {"type":"message","content":[{"type":"output_text","text":"수유 2회, 총 200ml를 기록했습니다."}]}]}
            """);
    private int status = 200;
    private final AtomicInteger calls = new AtomicInteger();

    @BeforeEach
    void server() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> {
            calls.incrementAndGet();
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        generator = new OpenAiDailyReportGenerator(mapper, "test-api-key", "gpt-5-mini", HttpClient.newHttpClient(), endpoint());
    }

    @AfterEach
    void stop() { server.stop(0); }

    @Test
    void responsesApiUsesOnlyAnonymousFactsAndFindsTextAfterReasoningOutput() {
        assertEquals("수유 2회, 총 200ml를 기록했습니다.", generator.generate(facts()));
        var sent = mapper.readTree(body.get());
        assertEquals("gpt-5-mini", sent.path("model").asString());
        assertFalse(sent.path("store").asBoolean());
        assertEquals(2048, sent.path("max_output_tokens").asInt());
        assertEquals("Bearer test-api-key", authorization.get());
        var input = mapper.readTree(sent.path("input").asString());
        assertEquals(200, input.path("totalFeedingMl").asLong());
        for (String key : new String[]{"babyId", "userId", "name", "nickname", "note", "transcript", "email"}) {
            assertFalse(input.has(key), key);
        }
    }

    @Test
    void missingKeyNeverMakesNetworkRequest() {
        var unconfigured = new OpenAiDailyReportGenerator(mapper, "", "gpt-5-mini", HttpClient.newHttpClient(), endpoint());
        assertUnavailable(() -> unconfigured.generate(facts()));
        assertEquals(0, calls.get());
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 500})
    void upstreamFailureIsGenericAndNotAutomaticallyRetried(int httpStatus) {
        status = httpStatus;
        response.set("provider-secret-details");
        var error = assertUnavailable(() -> generator.generate(facts()));
        assertFalse(error.getMessage().contains("provider-secret-details"));
        assertEquals(1, calls.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not json",
            "{\"status\":\"incomplete\",\"output\":[]}",
            "{\"status\":\"completed\",\"output\":[]}",
            "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"refusal\",\"refusal\":\"no\"}]}]}",
            "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":123}]}]}"
    })
    void incompleteRefusedOrMalformedOutputsAreNotSavedAsSummaries(String payload) {
        response.set(payload);
        assertUnavailable(() -> generator.generate(facts()));
    }

    @Test
    void interruptedCallsPreserveThreadInterruptFlag() throws Exception {
        var client = org.mockito.Mockito.mock(HttpClient.class);
        org.mockito.Mockito.when(client.send(org.mockito.ArgumentMatchers.any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenThrow(new InterruptedException());
        var interrupted = new OpenAiDailyReportGenerator(mapper, "test-api-key", "gpt-5-mini", client, endpoint());
        try {
            assertUnavailable(() -> interrupted.generate(facts()));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void networkTimeoutReturnsGenericFailureWithoutRetrying() throws Exception {
        var client = org.mockito.Mockito.mock(HttpClient.class);
        org.mockito.Mockito.when(client.send(org.mockito.ArgumentMatchers.any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenThrow(new java.net.http.HttpTimeoutException("provider-secret-details"));
        var timedOut = new OpenAiDailyReportGenerator(mapper, "test-api-key", "gpt-5-mini", client, endpoint());
        var error = assertUnavailable(() -> timedOut.generate(facts()));
        assertFalse(error.getMessage().contains("provider-secret-details"));
        org.mockito.Mockito.verify(client, org.mockito.Mockito.times(1)).send(
                org.mockito.ArgumentMatchers.any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }

    @Test
    void oversizedSummaryIsRejectedWithoutTruncatingIt() {
        response.set(mapper.writeValueAsString(java.util.Map.of("status", "completed", "output", java.util.List.of(
                java.util.Map.of("type", "message", "content", java.util.List.of(
                        java.util.Map.of("type", "output_text", "text", "x".repeat(8001))))))));
        assertUnavailable(() -> generator.generate(facts()));
    }

    private DailyReportGenerationException assertUnavailable(org.junit.jupiter.api.function.Executable action) {
        var error = assertThrows(DailyReportGenerationException.class, action);
        assertEquals(ErrorCode.DAILY_REPORT_GENERATION_UNAVAILABLE, error.getErrorCode());
        assertFalse(error.getMessage().contains("test-api-key"));
        return error;
    }

    private URI endpoint() { return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/responses"); }

    private DailyReportFacts facts() {
        return new DailyReportFacts(LocalDate.of(2026, 11, 8), 2, 2, 200, 1, 0, 60, 1, 36.5, 36.5, 36.5, 2, 1);
    }
}
