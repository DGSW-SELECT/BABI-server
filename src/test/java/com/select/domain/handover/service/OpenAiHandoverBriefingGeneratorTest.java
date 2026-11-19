package com.select.domain.handover.service;

import com.select.domain.handover.exception.HandoverBriefingGenerationException;
import com.select.domain.report.dto.response.DailyReportFacts;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiHandoverBriefingGeneratorTest {

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> responseBody = new AtomicReference<>("""
            {"status":"completed","output":[
              {"type":"message","content":[{"type":"output_text","text":"수유 2회와 수면 60분을 기록했습니다.\\n한 줄 정리: 수유와 수면 기록을 확인했습니다."}]}
            ]}
            """);
    private final AtomicInteger calls = new AtomicInteger();
    private HttpServer server;
    private int responseStatus = 200;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> {
            calls.incrementAndGet();
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsOnlyAnonymousNumericFactsToResponsesApi() {
        OpenAiHandoverBriefingGenerator generator = generator("test-api-key");

        assertEquals("수유 2회와 수면 60분을 기록했습니다.\n한 줄 정리: 수유와 수면 기록을 확인했습니다.",
                generator.generate(facts()));

        var request = mapper.readTree(requestBody.get());
        assertEquals("gpt-5-mini", request.path("model").asString());
        assertFalse(request.path("store").asBoolean());
        assertEquals(1200, request.path("max_output_tokens").asInt());
        assertTrue(request.path("instructions").asString()
                .contains("반드시 '한 줄 정리: '로 시작하세요"));
        assertEquals("Bearer test-api-key", authorization.get());
        var input = mapper.readTree(request.path("input").asString());
        assertEquals(240, input.path("totalFeedingMl").asLong());
        for (String field : new String[]{"babyId", "userId", "name", "email", "note", "transcript", "memo"}) {
            assertFalse(input.has(field), field);
        }
    }

    @Test
    void missingKeyDoesNotCallProvider() {
        assertThrows(HandoverBriefingGenerationException.class, () -> generator(" ").generate(facts()));
        assertEquals(0, calls.get());
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 500})
    void providerErrorsAreGenericAndNotRetried(int status) {
        responseStatus = status;
        responseBody.set("provider-secret-details");

        HandoverBriefingGenerationException exception = assertThrows(
                HandoverBriefingGenerationException.class, () -> generator("test-api-key").generate(facts()));

        assertFalse(exception.getMessage().contains("provider-secret-details"));
        assertEquals(1, calls.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not json",
            "{\"status\":\"incomplete\",\"output\":[]}",
            "{\"status\":\"completed\",\"output\":[]}",
            "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"refusal\"}]}]}"
    })
    void malformedIncompleteOrRefusedOutputFails(String payload) {
        responseBody.set(payload);
        assertThrows(HandoverBriefingGenerationException.class,
                () -> generator("test-api-key").generate(facts()));
    }

    @Test
    void oversizedOutputIsRejectedInsteadOfTruncated() {
        responseBody.set(mapper.writeValueAsString(Map.of(
                "status", "completed",
                "output", java.util.List.of(Map.of(
                        "type", "message",
                        "content", java.util.List.of(Map.of("type", "output_text", "text", "x".repeat(8001)))
                ))
        )));

        assertThrows(HandoverBriefingGenerationException.class,
                () -> generator("test-api-key").generate(facts()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "수유 2회를 기록했습니다.",
            "수유 2회를 기록했습니다.\n한 줄 정리: ",
            "한 줄 정리: 수유 기록을 확인했습니다.\n추가 본문"
    })
    void outputWithoutFinalOneLineSummaryIsRejected(String output) {
        responseBody.set(mapper.writeValueAsString(Map.of(
                "status", "completed",
                "output", java.util.List.of(Map.of(
                        "type", "message",
                        "content", java.util.List.of(Map.of("type", "output_text", "text", output))
                ))
        )));

        assertThrows(HandoverBriefingGenerationException.class,
                () -> generator("test-api-key").generate(facts()));
    }

    private OpenAiHandoverBriefingGenerator generator(String apiKey) {
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/responses");
        return new OpenAiHandoverBriefingGenerator(
                mapper, apiKey, "gpt-5-mini", HttpClient.newHttpClient(), endpoint);
    }

    private DailyReportFacts facts() {
        return new DailyReportFacts(LocalDate.of(2026, 11, 17), 2, 2, 240,
                1, 0, 60, 1, 36.7, 36.7, 36.7, 1, 0);
    }
}
