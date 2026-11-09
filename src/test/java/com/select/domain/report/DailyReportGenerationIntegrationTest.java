package com.select.domain.report;

import com.select.domain.baby.entity.BabyMember;
import com.select.domain.baby.entity.BabyRole;
import com.select.domain.baby.repository.BabyMemberRepository;
import com.select.domain.baby.request.BabyCreateRequest;
import com.select.domain.baby.service.BabyService;
import com.select.domain.report.dto.response.DailyReportFacts;
import com.select.domain.report.exception.DailyReportGenerationException;
import com.select.domain.report.service.OpenAiDailyReportGenerator;
import com.select.domain.user.entity.User;
import com.select.domain.user.repository.UserRepository;
import com.select.global.exception.ErrorCode;
import com.select.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:report-generation;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=10000",
        "spring.jpa.hibernate.ddl-auto=validate"
})
public class DailyReportGenerationIntegrationTest {
    @LocalServerPort private int port;
    @Autowired private UserRepository users;
    @Autowired private BabyService babies;
    @Autowired private BabyMemberRepository members;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtProvider jwt;
    @Autowired private ObjectMapper mapper;
    @MockitoBean private OpenAiDailyReportGenerator generator;
    private Long ownerId, outsiderId, babyId;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    @BeforeEach
    void fixture() {
        ownerId = user();
        outsiderId = user();
        babyId = babies.createBaby(new BabyCreateRequest("아기", LocalDate.of(2026, 1, 1)), ownerId).babyId();
        when(generator.generate(any())).thenReturn("수유·수면·체온 등 하루 기록을 요약했습니다.");
    }

    @Test
    void generatedReportIncludesAllFiveTypesAndClipsSleepToTheRequestedDay() throws Exception {
        seed();
        var response = generate(ownerId);
        assertEquals(201, response.statusCode(), response.body());
        var data = mapper.readTree(response.body()).path("data");
        assertEquals("수유·수면·체온 등 하루 기록을 요약했습니다.", data.path("summary").asString());
        assertEquals(200 / 7.0, data.path("sevenDayAverage").path("averageDailyFeedingAmountMl").asDouble(), 0.001);
        var facts = ArgumentCaptor.forClass(DailyReportFacts.class);
        verify(generator).generate(facts.capture());
        assertEquals(LocalDate.of(2026, 11, 8), facts.getValue().date());
        assertEquals(200, facts.getValue().totalFeedingMl());
        assertEquals(420, facts.getValue().completedSleepMinutes());
        assertEquals(36.5, facts.getValue().averageTemperatureCelsius());
        assertEquals(1, facts.getValue().diaperCount());
        assertEquals(1, facts.getValue().cryCount());
        assertEquals(200, request("GET", base() + "/2026-11-08", ownerId, null).statusCode());
        assertEquals(409, generate(ownerId).statusCode());
        verify(generator, times(1)).generate(any());
    }

    @Test
    void noRecordsAndUnauthorizedRequestsNeverCallTheProvider() throws Exception {
        var empty = generate(ownerId);
        assertEquals(400, empty.statusCode());
        assertEquals("DAILY_REPORT_NO_RECORDS", mapper.readTree(empty.body()).path("error").path("code").asString());
        assertEquals(403, generate(outsiderId).statusCode());
        assertEquals(401, generate(null).statusCode());
        verifyNoInteractions(generator);
    }

    @Test
    void readOnlyGuardianCannotTriggerPaidGeneration() throws Exception {
        Long reader = user();
        Long member = members.saveAndFlush(BabyMember.builder().babyId(babyId).userId(reader).role(BabyRole.VIEWER).build()).getId();
        jdbc.update("INSERT INTO baby_permissions(baby_id,baby_member_id,permission) VALUES (?,?,'REPORT_READ')", babyId, member);
        seed();
        assertEquals(403, generate(reader).statusCode());
        verifyNoInteractions(generator);
    }

    @Test
    void providerFailureReturns503WithoutSavingAFakeCompletedReport() throws Exception {
        seed();
        when(generator.generate(any())).thenThrow(new DailyReportGenerationException(ErrorCode.DAILY_REPORT_GENERATION_UNAVAILABLE));
        var response = generate(ownerId);
        assertEquals(503, response.statusCode());
        assertEquals("DAILY_REPORT_GENERATION_UNAVAILABLE", mapper.readTree(response.body()).path("error").path("code").asString());
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM daily_reports WHERE baby_id=?", Long.class, babyId));
        assertEquals(201, request("POST", base(), ownerId, "{\"reportDate\":\"2026-11-08\",\"summary\":\"manual summary\"}").statusCode());
    }

    @Test
    void concurrentGenerationMakesOnlyOneProviderCallAndOneReport() throws Exception {
        seed();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return generate(ownerId).statusCode(); });
            var second = executor.submit(() -> { start.await(); return generate(ownerId).statusCode(); });
            start.countDown();
            var statuses = java.util.List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertTrue(statuses.contains(201), statuses.toString());
            assertTrue(statuses.contains(409), statuses.toString());
        }
        verify(generator, times(1)).generate(any());
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM daily_reports WHERE baby_id=?", Long.class, babyId));
    }

    @Test
    void ongoingSleepIsCountedWithoutInventingItsDurationAndNextDayRecordsAreExcluded() throws Exception {
        jdbc.update("INSERT INTO sleep_records(baby_id,sleep_start_at) VALUES (?,'2026-11-08 22:00:00')", babyId);
        jdbc.update("INSERT INTO sleep_records(baby_id,sleep_start_at) VALUES (?,'2026-11-09 00:00:00')", babyId);
        jdbc.update("INSERT INTO sleep_records(baby_id,sleep_start_at,sleep_end_at) VALUES (?,'2026-11-07 23:00:00','2026-11-08 00:00:00')", babyId);
        assertEquals(201, generate(ownerId).statusCode());
        var facts = ArgumentCaptor.forClass(DailyReportFacts.class);
        verify(generator).generate(facts.capture());
        assertEquals(1, facts.getValue().ongoingSleepCount());
        assertEquals(0, facts.getValue().completedSleepCount());
        assertEquals(0, facts.getValue().completedSleepMinutes());
    }

    private void seed() {
        jdbc.update("INSERT INTO feeding_records(baby_id,amount_ml,occurred_at) VALUES (?,200,'2026-11-08 08:00:00')", babyId);
        jdbc.update("INSERT INTO sleep_records(baby_id,sleep_start_at,sleep_end_at) VALUES (?,'2026-11-07 23:00:00','2026-11-08 07:00:00')", babyId);
        jdbc.update("INSERT INTO temperature_records(baby_id,temperature_celsius,measured_at) VALUES (?,36.5,'2026-11-08 09:00:00')", babyId);
        jdbc.update("INSERT INTO diaper_records(baby_id,diaper_type,occurred_at) VALUES (?,'URINE','2026-11-08 10:00:00')", babyId);
        jdbc.update("INSERT INTO cry_records(baby_id,cry_type,occurred_at) VALUES (?,'HUNGER','2026-11-08 11:00:00')", babyId);
    }

    private Long user() {
        return users.saveAndFlush(User.builder().email(UUID.randomUUID()+"@example.invalid")
                .passwordHash("unused-test-hash").nickname("테스트 보호자").build()).getId();
    }

    private String base() { return "/babi/api/babies/" + babyId + "/daily-reports"; }

    private HttpResponse<String> generate(Long userId) throws Exception {
        return request("POST", base() + "/2026-11-08/generate", userId, null);
    }

    private HttpResponse<String> request(String method, String path, Long userId, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json");
        if (userId != null) builder.header("Authorization", "Bearer " + jwt.createAccessToken(userId.toString()));
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
