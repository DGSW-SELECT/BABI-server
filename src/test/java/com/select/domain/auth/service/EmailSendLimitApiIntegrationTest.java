package com.select.domain.auth.service;

import com.select.domain.auth.mail.EmailSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.util.UUID;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "BABI_EMAIL_SEND_GLOBAL_PER_MINUTE=2", "BABI_EMAIL_SEND_SOURCE_PER_MINUTE=2",
        "server.forward-headers-strategy=none"
})
@EnabledIfEnvironmentVariable(named = "BABI_REDIS_TEST_PORT", matches = "[0-9]+")
class EmailSendLimitApiIntegrationTest {
    @DynamicPropertySource static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> System.getenv("BABI_REDIS_TEST_PORT"));
    }
    @LocalServerPort int port;
    @Autowired StringRedisTemplate redis;
    @Autowired ObjectMapper json;
    @MockitoBean EmailSender sender;

    @Test void differentEmailsAndForgedForwardedHeadersCannotBypassLimit() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String first = "first" + suffix + "@example.invalid";
        String second = "second" + suffix + "@example.invalid";
        String third = "third" + suffix + "@example.invalid";
        var client = HttpClient.newHttpClient();
        try {
            redis.delete("email:send-limit:global");
            assertEquals(200, send(client, first, "192.0.2.1").statusCode());
            assertEquals(200, send(client, second, "192.0.2.2").statusCode());
            var blocked = send(client, third, "192.0.2.3");
            assertEquals(429, blocked.statusCode());
            assertEquals("EMAIL_SEND_RATE_LIMITED", json.readTree(blocked.body()).path("error").path("code").asString());
            verify(sender, times(2)).send(anyString(), anyString(), anyString());
        } finally {
            redis.delete("email:send-limit:global");
            for (String email : List.of(first, second, third)) redis.delete(List.of("email:verification:" + email,
                    "email:verified:" + email, "email:resend:" + email, "email:attempts:" + email));
        }
    }
    private HttpResponse<String> send(HttpClient client, String email, String forwarded) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/auth/email/send"))
                .header("Content-Type", "application/json").header("X-Forwarded-For", forwarded)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(java.util.Map.of("email", email)))).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
