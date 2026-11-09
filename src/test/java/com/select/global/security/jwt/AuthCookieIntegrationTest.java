package com.select.global.security.jwt;

import com.select.domain.user.entity.User;
import com.select.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"auth.cookies.secure=false", "auth.cors.allowed-origins=https://frontend.example.invalid"})
@EnabledIfEnvironmentVariable(named = "BABI_REDIS_TEST_PORT", matches = "[0-9]+")
class AuthCookieIntegrationTest {
    @DynamicPropertySource static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> System.getenv("BABI_REDIS_TEST_PORT"));
    }
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired RefreshSessionService sessions;
    @LocalServerPort int port;
    final HttpClient client = HttpClient.newHttpClient();

    @Test void loginRefreshLogoutAndCsrf() throws Exception {
        String email = UUID.randomUUID() + "@example.invalid";
        users.saveAndFlush(User.builder().email(email).nickname("cookie").passwordHash(passwords.encode("Test1234!")).build());
        var login = send("POST", "/auth/login", null, null, "{\"email\":\"" + email + "\",\"password\":\"Test1234!\"}");
        assertEquals(200, login.statusCode());
        String cookies = cookies(login);
        String csrf = cookie(login, AuthCookies.CSRF);
        var csrfResponse = send("GET", "/auth/csrf", cookies, null, null);
        assertEquals(200, csrfResponse.statusCode());
        assertTrue(csrfResponse.body().contains(csrf));
        assertEquals(200, send("GET", "/babies", cookies, null, null).statusCode());
        assertEquals(403, send("POST", "/auth/refresh", cookies, null, null).statusCode());
        var refresh = send("POST", "/auth/refresh", cookies, csrf, null);
        assertEquals(200, refresh.statusCode());
        assertEquals(401, send("POST", "/auth/refresh", cookies, csrf, null).statusCode());
        String updated = cookies(refresh);
        String updatedCsrf = cookie(refresh, AuthCookies.CSRF);
        var logout = send("POST", "/auth/logout", updated, updatedCsrf, null);
        assertEquals(200, logout.statusCode());
        assertTrue(logout.headers().allValues("Set-Cookie").stream().allMatch(v -> v.contains("Max-Age=0")));
        assertEquals(401, send("GET", "/babies", updated, null, null).statusCode());
        assertEquals(401, send("POST", "/auth/refresh", updated, updatedCsrf, null).statusCode());
    }

    @Test void corsOnlyAllowsConfiguredOrigin() throws Exception {
        for (String origin : List.of("https://frontend.example.invalid", "https://untrusted.example.invalid")) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/auth/refresh"))
                    .header("Origin", origin).header("Access-Control-Request-Method", "POST")
                    .header("Access-Control-Request-Headers", "x-csrf-token")
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (origin.contains("untrusted")) assertEquals(403, response.statusCode());
            else {
                assertEquals(200, response.statusCode());
                assertEquals(origin, response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
                assertEquals("true", response.headers().firstValue("Access-Control-Allow-Credentials").orElseThrow());
            }
        }
    }

    @Test void simultaneousRotationAllowsOnlyOneRequest() throws Exception {
        String token = sessions.create("123");
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var tasks = List.of(pool.submit(() -> rotate(start, token)), pool.submit(() -> rotate(start, token)));
            start.countDown();
            assertEquals(1, tasks.stream().filter(f -> { try { return f.get(); } catch (Exception e) { throw new RuntimeException(e); } }).count());
        }
    }

    @Test void logoutOfOriginalCredentialInvalidatesRotatedSession() {
        String original = sessions.create("123");
        var rotated = sessions.rotate(original);
        String sessionId = RefreshSessionService.sessionId(rotated.token());
        assertTrue(sessions.isActive(sessionId));
        sessions.revoke(original);
        assertFalse(sessions.isActive(sessionId));
        assertThrows(com.select.domain.user.exception.AccountLifecycleException.class, () -> sessions.rotate(rotated.token()));
    }
    private boolean rotate(CountDownLatch start, String token) throws Exception {
        start.await();
        try { sessions.rotate(token); return true; }
        catch (com.select.domain.user.exception.AccountLifecycleException e) { return false; }
    }
    private HttpResponse<String> send(String method, String path, String cookies, String csrf, String body) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (cookies != null) b.header("Cookie", cookies);
        if (csrf != null) b.header("X-CSRF-Token", csrf);
        if (body != null) b.header("Content-Type", "application/json");
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
    private String cookies(HttpResponse<String> response) {
        return String.join("; ", response.headers().allValues("Set-Cookie").stream().map(v -> v.split(";", 2)[0]).toList());
    }
    private String cookie(HttpResponse<String> response, String name) {
        return response.headers().allValues("Set-Cookie").stream().filter(v -> v.startsWith(name + "="))
                .findFirst().orElseThrow().split(";", 2)[0].substring(name.length() + 1);
    }
}
