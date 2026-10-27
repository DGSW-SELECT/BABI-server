package com.select.domain.report;

import com.select.domain.report.repository.DailyReportStatisticsRepository;
import com.select.domain.user.entity.User;
import com.select.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:report-integration;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class DailyReportIntegrationTest {

    private static final long BABY_ID = 300L;
    private static final LocalDate REPORT_DATE = LocalDate.of(2026, 10, 20);
    private static final String REPORT_URL = "/babi/api/babies/" + BABY_ID + "/daily-reports";
    private static final String PASSWORD = "Secret123!";
    private static final String CREATE_REQUEST = """
            {"reportDate":"2026-10-20","summary":"daily summary"}
            """;

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DailyReportStatisticsRepository statisticsRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM daily_reports");
        jdbcTemplate.update("DELETE FROM feeding_records");
        jdbcTemplate.update("DELETE FROM sleep_records");
        jdbcTemplate.update("DELETE FROM temperature_records");
        jdbcTemplate.update("DELETE FROM baby_permissions");
        jdbcTemplate.update("DELETE FROM family_members");
        jdbcTemplate.update("DELETE FROM babies");
        jdbcTemplate.update("DELETE FROM family_groups");
        userRepository.deleteAll();
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "GET", "PATCH", "DELETE"})
    void rejectsUnauthenticatedCrud(String method) throws Exception {
        var response = reportRequest(method, null, requestBody(method));
        assertEquals(401, response.statusCode(), response.body());
    }

    @Test
    void ownerCanCreateReadUpdateAndDeleteReportOnFreshSchema() throws Exception {
        User owner = createUser("owner@example.com");
        createBaby(owner.getId());
        String token = loginToken(owner.getEmail());

        var created = reportRequest("POST", token, CREATE_REQUEST);
        assertEquals(201, created.statusCode(), created.body());
        var average = objectMapper.readTree(created.body()).path("data").path("sevenDayAverage");
        assertEquals(0.0, average.path("averageDailyFeedingAmountMl").asDouble());
        assertEquals(0.0, average.path("averageDailySleepMinutes").asDouble());
        assertTrue(average.path("averageTemperatureCelsius").isNull());

        assertEquals(409, reportRequest("POST", token, CREATE_REQUEST).statusCode());
        assertEquals(200, reportRequest("GET", token, null).statusCode());
        var updated = reportRequest("PATCH", token, "{\"summary\":\"changed\"}");
        assertEquals(200, updated.statusCode(), updated.body());
        assertEquals("changed", objectMapper.readTree(updated.body()).path("data").path("summary").asString());
        assertEquals(200, reportRequest("DELETE", token, null).statusCode());
        assertEquals(404, reportRequest("GET", token, null).statusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "GET", "PATCH", "DELETE"})
    void rejectsOtherUserBeforeReadingOrChangingReports(String method) throws Exception {
        User owner = createUser("owner@example.com");
        createBaby(owner.getId());
        jdbcTemplate.update("""
                INSERT INTO daily_reports (baby_id, report_date, summary, status, generated_at)
                VALUES (?, ?, 'private', 'COMPLETED', CURRENT_TIMESTAMP)
                """, BABY_ID, REPORT_DATE);
        String token = loginToken(createUser("other@example.com").getEmail());

        var response = reportRequest(method, token, requestBody(method));
        assertEquals(403, response.statusCode(), response.body());
        assertEquals("FORBIDDEN", objectMapper.readTree(response.body()).path("error").path("code").asString());
        assertEquals("private", jdbcTemplate.queryForObject("SELECT summary FROM daily_reports", String.class));
    }

    @Test
    void familyMemberNeedsPermissionForTheRequestedBaby() throws Exception {
        User owner = createUser("owner@example.com");
        createBaby(owner.getId());
        User member = createUser("member@example.com");
        jdbcTemplate.update("""
                INSERT INTO family_members (id, family_group_id, user_id, `role`)
                VALUES (200, 100, ?, 'GUARDIAN')
                """, member.getId());
        String token = loginToken(member.getEmail());
        assertEquals(403, reportRequest("POST", token, CREATE_REQUEST).statusCode());

        jdbcTemplate.update("INSERT INTO baby_permissions (baby_id, family_member_id, permission) VALUES (?, 200, 'REPORT')",
                BABY_ID);
        assertEquals(201, reportRequest("POST", token, CREATE_REQUEST).statusCode());
        assertEquals(200, reportRequest("GET", token, null).statusCode());
        jdbcTemplate.update("DELETE FROM baby_permissions");
        assertEquals(403, reportRequest("GET", token, null).statusCode());
    }

    @ParameterizedTest
    @CsvSource({
            "2026-10-13T23:00:00, 2026-10-14T07:00:00, 420",
            "2026-10-20T23:00:00, 2026-10-21T07:00:00, 60",
            "2026-10-13T23:00:00, 2026-10-21T07:00:00, 10080",
            "2026-10-13T22:00:00, 2026-10-14T00:00:00, 0",
            "2026-10-21T00:00:00, 2026-10-21T07:00:00, 0"
    })
    void sleepAverageUsesOnlyTimeInsideSevenDayWindow(String start, String end, long minutes) {
        createBaby(createUser("owner@example.com").getId());
        jdbcTemplate.update("""
                INSERT INTO sleep_records (baby_id, sleep_start_at, sleep_end_at, created_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """, BABY_ID, java.time.LocalDateTime.parse(start), java.time.LocalDateTime.parse(end));

        assertEquals(minutes / 7.0, statisticsRepository.findAverage(BABY_ID, REPORT_DATE).averageDailySleepMinutes());
    }

    private User createUser(String email) {
        return userRepository.saveAndFlush(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .nickname("guardian")
                .build());
    }

    private void createBaby(long ownerUserId) {
        jdbcTemplate.update("""
                INSERT INTO family_groups (id, name, owner_user_id, created_at)
                VALUES (100, 'family', ?, CURRENT_TIMESTAMP)
                """, ownerUserId);
        jdbcTemplate.update("""
                INSERT INTO babies (id, family_group_id, name, birth_date, created_at)
                VALUES (?, 100, 'baby', '2026-01-01', CURRENT_TIMESTAMP)
                """, BABY_ID);
    }

    private String loginToken(String email) throws Exception {
        var response = send("POST", "/auth/login", null,
                objectMapper.writeValueAsString(java.util.Map.of("email", email, "password", PASSWORD)));
        assertEquals(200, response.statusCode(), response.body());
        String token = objectMapper.readTree(response.body()).path("data").path("accessToken").asString();
        assertFalse(token.isBlank());
        return token;
    }

    private String requestBody(String method) {
        return switch (method) {
            case "POST" -> CREATE_REQUEST;
            case "PATCH" -> "{\"summary\":\"stolen\"}";
            default -> null;
        };
    }

    private HttpResponse<String> reportRequest(String method, String token, String body) throws Exception {
        String path = "POST".equals(method) ? REPORT_URL : REPORT_URL + "/" + REPORT_DATE;
        return send(method, path, token, body);
    }

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
