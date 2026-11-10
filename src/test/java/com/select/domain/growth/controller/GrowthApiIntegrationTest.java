package com.select.domain.growth.controller;

import com.select.domain.baby.entity.Baby;
import com.select.domain.baby.entity.BabyMember;
import com.select.domain.baby.entity.BabyRole;
import com.select.domain.baby.repository.BabyMemberRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.user.entity.User;
import com.select.domain.user.repository.UserRepository;
import com.select.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GrowthApiIntegrationTest {
    @Autowired UserRepository users;
    @Autowired BabyRepository babies;
    @Autowired BabyMemberRepository members;
    @Autowired JwtProvider jwt;
    @Autowired ObjectMapper json;
    @LocalServerPort int port;
    final HttpClient http = HttpClient.newHttpClient();

    @Test void jwtCreateListCompareAndAccessControl() throws Exception {
        var user = users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.invalid").nickname("parent").passwordHash("hash").build());
        var other = users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.invalid").nickname("other").passwordHash("hash").build());
        var baby = babies.saveAndFlush(Baby.builder().name("baby").birthDate(LocalDate.of(2025,1,1)).createdByUserId(user.getId()).build());
        members.saveAndFlush(BabyMember.builder().babyId(baby.getId()).userId(user.getId()).role(BabyRole.OWNER).build());
        String path = "/babies/" + baby.getId();
        String token = jwt.createAccessToken(user.getId().toString());
        LocalDate today = LocalDate.now();
        var first = send("POST", path + "/growth-records", token, "{\"measuredDate\":\"" + today.minusDays(2) + "\",\"heightCm\":70.0}");
        assertEquals(201, first.statusCode(), first.body());
        assertEquals(0, new java.math.BigDecimal("70.0").compareTo(json.readTree(first.body()).get("heightCm").decimalValue()));
        assertTrue(json.readTree(first.body()).get("authorMemberId").asLong() > 0);
        var second = send("POST", path + "/growth-records", token, "{\"measuredDate\":\"" + today.minusDays(1) + "\",\"heightCm\":71.5,\"weightKg\":9.2}");
        assertEquals(201, second.statusCode(), second.body());
        long id = json.readTree(second.body()).get("growthRecordId").asLong();
        var list = send("GET", path + "/growth-records?limit=1", token, null);
        assertEquals(200, list.statusCode(), list.body());
        assertEquals(id, json.readTree(list.body()).get("item").get(0).get("growthRecordId").asLong());
        long nextCursor = json.readTree(list.body()).get("nextCursor").asLong();
        assertTrue(nextCursor > 0);
        var next = send("GET", path + "/growth-records?limit=1&cursor=" + nextCursor, token, null);
        assertEquals(200, next.statusCode(), next.body());
        assertEquals(1, json.readTree(next.body()).get("item").size());
        assertTrue(json.readTree(next.body()).get("nextCursor").isNull());
        var filtered = send("GET", path + "/growth-records?from=" + today.minusDays(1), token, null);
        assertEquals(1, json.readTree(filtered.body()).get("item").size());
        var compare = send("GET", path + "/growth-comparison?growthRecordId=" + id, token, null);
        assertEquals(200, compare.statusCode(), compare.body());
        assertEquals(0, new java.math.BigDecimal("1.5").compareTo(json.readTree(compare.body()).get("change").get("heightCm").decimalValue()));
        assertEquals("SEX_REQUIRED", json.readTree(compare.body()).get("standard").get("status").asText());
        var updatedBaby = send("PATCH", path, token, "{\"sex\":\"FEMALE\"}");
        assertEquals(200, updatedBaby.statusCode(), updatedBaby.body());
        var updatedComparison = send("GET", path + "/growth-comparison?growthRecordId=" + id, token, null);
        assertEquals("AVAILABLE", json.readTree(updatedComparison.body()).get("standard").get("status").asText());
        assertEquals("FEMALE", json.readTree(updatedComparison.body()).get("standard").get("sex").asText());
        assertEquals(401, send("GET", path + "/growth-records", null, null).statusCode());
        String otherToken = jwt.createAccessToken(other.getId().toString());
        assertEquals(404, send("GET", path + "/growth-records", otherToken, null).statusCode());
        assertEquals(404, send("GET", path + "/growth-comparison?growthRecordId=" + id, otherToken, null).statusCode());
        assertEquals(404, send("POST", path + "/growth-records", otherToken, "{\"measuredDate\":\"" + today + "\",\"heightCm\":70}").statusCode());
    }
    @Test void standardComparisonUsesStoredSexAndExistingRecord() throws Exception {
        var user = users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.invalid").nickname("parent").passwordHash("hash").build());
        var baby = babies.saveAndFlush(Baby.builder().name("baby").birthDate(LocalDate.of(2025,1,1)).sex(com.select.domain.baby.entity.BabySex.MALE).createdByUserId(user.getId()).build());
        members.saveAndFlush(BabyMember.builder().babyId(baby.getId()).userId(user.getId()).role(BabyRole.OWNER).build());
        String path = "/babies/" + baby.getId();
        String token = jwt.createAccessToken(user.getId().toString());
        var created = send("POST", path + "/growth-records", token,
                "{\"measuredDate\":\"2026-01-01\",\"heightCm\":75,\"weightKg\":9}");
        assertEquals(201, created.statusCode(), created.body());
        long id = json.readTree(created.body()).get("growthRecordId").asLong();
        var result = send("GET", path + "/growth-comparison?growthRecordId=" + id, token, null);
        assertEquals(200, result.statusCode(), result.body());
        var standard = json.readTree(result.body()).get("standard");
        assertEquals("AVAILABLE", standard.get("status").asText());
        assertEquals("MALE", standard.get("sex").asText());
        assertEquals(12, standard.get("ageMonths").asInt());
        assertEquals(0, new java.math.BigDecimal("37.6").compareTo(standard.get("heightPercentile").decimalValue()));
        assertEquals(0, new java.math.BigDecimal("26.3").compareTo(standard.get("weightPercentile").decimalValue()));
    }
    @Test void simultaneousSameDateCreatesOnlyOneRecord() throws Exception {
        var user = users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.invalid").nickname("parent").passwordHash("hash").build());
        var baby = babies.saveAndFlush(Baby.builder().name("baby").birthDate(LocalDate.of(2025,1,1)).createdByUserId(user.getId()).build());
        members.saveAndFlush(BabyMember.builder().babyId(baby.getId()).userId(user.getId()).role(BabyRole.OWNER).build());
        String token = jwt.createAccessToken(user.getId().toString());
        String path = "/babies/" + baby.getId() + "/growth-records";
        String body = "{\"measuredDate\":\"" + LocalDate.now() + "\",\"heightCm\":70.0}";
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { start.await(); return send("POST", path, token, body).statusCode(); });
            var b = pool.submit(() -> { start.await(); return send("POST", path, token, body).statusCode(); });
            start.countDown();
            assertEquals(java.util.Set.of(201, 409), java.util.Set.of(a.get(), b.get()));
        }
    }
    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.header("Content-Type", "application/json");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
