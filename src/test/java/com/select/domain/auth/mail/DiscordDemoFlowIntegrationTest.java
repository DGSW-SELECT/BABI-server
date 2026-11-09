package com.select.domain.auth.mail;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:discord-flow;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "baby.invitation.link-base-url=https://app.example.invalid/invite"
})
@EnabledIfEnvironmentVariable(named = "BABI_REDIS_TEST_PORT", matches = "[0-9]+")
class DiscordDemoFlowIntegrationTest {
    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> System.getenv("BABI_REDIS_TEST_PORT"));
    }

    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @MockitoBean EmailSender sender;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private HttpServer webhook;
    private final LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();

    @AfterEach void stopMock() { if (webhook != null) webhook.stop(0); }

    @Test void signupLoginAndInviteThroughDiscordTransport() throws Exception {
        webhook = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        webhook.createContext("/webhook", exchange -> {
            messages.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        webhook.start();
        var discord = new DiscordWebhookEmailSender(URI.create("http://127.0.0.1:" + webhook.getAddress().getPort() + "/webhook"), json, client);
        doAnswer(call -> { discord.send(call.getArgument(0), call.getArgument(1), call.getArgument(2)); return null; })
                .when(sender).send(anyString(), anyString(), anyString());
        String unique = UUID.randomUUID().toString().replace("-", "");
        String ownerEmail = "owner" + unique + "@example.invalid";
        String guestEmail = "guest" + unique + "@example.invalid";
        String owner = signupAndLogin(ownerEmail);
        JsonNode baby = request("POST", "/babies", owner, Map.of("name", "시연 아기", "birthDate", "2025-01-01"), 200);
        long babyId = baby.path("data").path("babyId").asLong();
        assertTrue(babyId > 0);
        String code = request("GET", "/babies/" + babyId + "/invitation-code", owner, null, 200).path("data").path("code").asString();
        assertTrue(code.matches("[A-Z0-9]{6}"));
        request("POST", "/babies/" + babyId + "/invitations", owner, Map.of("email", guestEmail), 200);
        String invitation = message();
        assertTrue(invitation.contains(code));
        assertFalse(invitation.contains(guestEmail));
        assertFalse(invitation.contains("https://"));
        String guest = signupAndLogin(guestEmail);
        request("POST", "/babies/invitations/check", guest, Map.of("code", code), 200);
        request("POST", "/babies/invitations/accept", guest, Map.of("code", code), 200);
        JsonNode members = request("GET", "/babies/" + babyId + "/members", owner, null, 200).path("data");
        assertEquals(2, members.size());
        assertTrue(members.toString().contains("OWNER"));
        assertTrue(members.toString().contains("GUARDIAN"));
    }

    @Test void timedOutInvitationReleasesLockAndKeepsCommittedCode() throws Exception {
        var invitationReceived = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        webhook = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        webhook.createContext("/webhook", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (json.readTree(body).path("content").asString().contains("보호자 초대")) {
                invitationReceived.countDown();
                try { release.await(12, TimeUnit.SECONDS); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            } else {
                messages.add(body);
                exchange.sendResponseHeaders(204, -1);
            }
            exchange.close();
        });
        webhook.start();
        var discord = new DiscordWebhookEmailSender(URI.create("http://127.0.0.1:" + webhook.getAddress().getPort() + "/webhook"), json, client);
        doAnswer(call -> { discord.send(call.getArgument(0), call.getArgument(1), call.getArgument(2)); return null; })
                .when(sender).send(anyString(), anyString(), anyString());
        String unique = UUID.randomUUID().toString().replace("-", "");
        String owner = signupAndLogin("owner" + unique + "@example.invalid");
        long babyId = request("POST", "/babies", owner, Map.of("name", "시연 아기", "birthDate", "2025-01-01"), 200)
                .path("data").path("babyId").asLong();
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var send = executor.submit(() -> request("POST", "/babies/" + babyId + "/invitations", owner,
                    Map.of("email", "guest" + unique + "@example.invalid"), 503));
            try {
                assertTrue(invitationReceived.await(5, TimeUnit.SECONDS));
                var lookup = executor.submit(() -> request("GET", "/babies/" + babyId + "/invitation-code", owner, null, 200));
                assertThrows(java.util.concurrent.TimeoutException.class, () -> lookup.get(300, TimeUnit.MILLISECONDS));
                assertEquals("INVITATION_SEND_FAILED", send.get(10, TimeUnit.SECONDS).path("error").path("code").asString());
                assertTrue(lookup.get(5, TimeUnit.SECONDS).path("data").path("code").asString().matches("[A-Z0-9]{6}"));
            } finally { release.countDown(); }
        }
    }

    private String signupAndLogin(String email) throws Exception {
        request("POST", "/auth/email/send", null, Map.of("email", email), 200);
        String body = message();
        assertFalse(body.contains(email));
        var matcher = Pattern.compile("인증번호: ([0-9]{6})").matcher(body);
        assertTrue(matcher.find());
        String proof = request("POST", "/auth/email/verify", null, Map.of("email", email, "code", matcher.group(1)), 200)
                .path("data").path("verificationToken").asString();
        assertFalse(proof.isBlank());
        request("POST", "/auth/signup", null, Map.of("email", email, "password", "Test1234!", "passwordConfirm", "Test1234!", "nickname", "시연", "verificationToken", proof), 201);
        String token = request("POST", "/auth/login", null, Map.of("email", email, "password", "Test1234!"), 200).path("data").path("accessToken").asString();
        assertFalse(token.isBlank());
        return token;
    }

    private String message() throws Exception {
        String payload = messages.poll(3, TimeUnit.SECONDS);
        assertNotNull(payload);
        JsonNode node = json.readTree(payload);
        assertEquals(0, node.path("allowed_mentions").path("parse").size());
        return node.path("content").asString();
    }

    private JsonNode request(String method, String path, String token, Object body, int expected) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(10));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body == null) request.GET();
        else request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(expected, response.statusCode(), response.body());
        return json.readTree(response.body());
    }
}
