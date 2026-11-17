package com.select.domain.report;

import com.select.domain.baby.entity.BabyMember;
import com.select.domain.baby.entity.BabyRole;
import com.select.domain.baby.repository.BabyMemberRepository;
import com.select.domain.baby.repository.BabyAccessRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:report-generation;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=10000",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.hikari.maximum-pool-size=5",
        "spring.datasource.hikari.minimum-idle=1",
        "spring.datasource.hikari.connection-timeout=1000"
})
public class DailyReportGenerationIntegrationTest {
    @LocalServerPort private int port;
    @Autowired private UserRepository users;
    @Autowired private BabyService babies;
    @Autowired private BabyMemberRepository members;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtProvider jwt;
    @Autowired private ObjectMapper mapper;
    @Autowired private DataSource dataSource;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoSpyBean private BabyAccessRepository access;
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
    void slowProviderReleasesConnectionsAndDuplicateRequestsDoNotBlockOtherBabies() throws Exception {
        seed();
        Long otherBaby = babies.createBaby(new BabyCreateRequest("다른 아기", LocalDate.of(2026, 1, 1)), ownerId).babyId();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(generator.generate(any())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "Provider must run outside a transaction");
            entered.countDown();
            assertTrue(release.await(15, TimeUnit.SECONDS));
            return "수유 기록 요약";
        });
        try (var executor = Executors.newFixedThreadPool(6)) {
            var first = executor.submit(() -> generate(ownerId));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS), "Provider did not start outside a transaction");
                var pool = dataSource.unwrap(com.zaxxer.hikari.HikariDataSource.class).getHikariPoolMXBean();
                assertEquals(0, pool.getActiveConnections(), "Provider must not hold a DB connection");
                var duplicates = IntStream.range(0, 5)
                        .mapToObj(i -> executor.submit(() -> generate(ownerId).statusCode())).toList();
                for (var duplicate : duplicates) assertEquals(409, duplicate.get(3, TimeUnit.SECONDS));
                assertEquals(404, request("GET", base() + "/2026-11-08", ownerId, null).statusCode());
                assertEquals(404, request("PATCH", base() + "/2026-11-08", ownerId, "{\"summary\":\"manual\"}").statusCode());
                assertEquals(404, request("DELETE", base() + "/2026-11-08", ownerId, null).statusCode());
                assertEquals(409, request("POST", base(), ownerId,
                        "{\"reportDate\":\"2026-11-08\",\"summary\":\"manual\"}").statusCode());
                assertEquals(200, request("GET", "/api/v1/babies/" + otherBaby + "/records?date=2026-11-08", ownerId, null).statusCode());
                assertEquals(201, request("POST", "/api/v1/babies/" + babyId + "/feedings", ownerId,
                        "{\"occurredAt\":\"2026-11-08T12:00:00\",\"feedingType\":\"FORMULA\",\"amountMl\":100}").statusCode());
                assertEquals(0, pool.getActiveConnections());
            } finally {
                release.countDown();
            }
            assertEquals(201, first.get(5, TimeUnit.SECONDS).statusCode());
        }
        verify(generator, times(1)).generate(any());
    }

    @Test
    void unauthorizedWritesNeverWaitForAnotherUsersBabyLock() throws Exception {
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(6)) {
            var holder = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbc.queryForObject("SELECT id FROM babies WHERE id=? FOR UPDATE", Long.class, babyId);
                locked.countDown();
                await(release);
            }));
            try {
                assertTrue(locked.await(5, TimeUnit.SECONDS));
                var requests = java.util.List.of(
                        executor.submit(() -> generate(outsiderId).statusCode()),
                        executor.submit(() -> request("POST", base(), outsiderId, "{\"reportDate\":\"2026-11-08\",\"summary\":\"manual\"}").statusCode()),
                        executor.submit(() -> request("PATCH", base() + "/2026-11-08", outsiderId, "{\"summary\":\"manual\"}").statusCode()),
                        executor.submit(() -> request("DELETE", base() + "/2026-11-08", outsiderId, null).statusCode()),
                        executor.submit(() -> request("POST", "/api/v1/babies/" + babyId + "/feedings", outsiderId,
                                "{\"occurredAt\":\"2026-11-08T12:00:00\",\"feedingType\":\"FORMULA\"}").statusCode()));
                for (var response : requests) assertEquals(403, response.get(3, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
            holder.get(5, TimeUnit.SECONDS);
        }
        verifyNoInteractions(generator);
    }

    @Test
    void membershipRevokedAfterPrecheckIsRejectedAfterAcquiringTheLock() throws Exception {
        for (boolean careRecord : new boolean[]{false, true}) {
            Long guardian = user();
            Long member = members.saveAndFlush(BabyMember.builder().babyId(babyId).userId(guardian).role(BabyRole.GUARDIAN).build()).getId();
            jdbc.update("INSERT INTO baby_permissions(baby_id,baby_member_id,permission) VALUES (?,?,'CARE_ALL')", babyId, member);
            var locked = new CountDownLatch(1);
            var checked = new CountDownLatch(1);
            doAnswer(invocation -> {
                Object result = invocation.callRealMethod();
                checked.countDown();
                return result;
            }).when(access).findMemberId(eq(guardian), eq(babyId), anyString(), eq(true));
            try (var executor = Executors.newSingleThreadExecutor()) {
                var revocation = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    jdbc.queryForObject("SELECT id FROM babies WHERE id=? FOR UPDATE", Long.class, babyId);
                    locked.countDown();
                    await(checked);
                    jdbc.update("UPDATE baby_members SET removed_at=CURRENT_TIMESTAMP WHERE id=?", member);
                }));
                assertTrue(locked.await(5, TimeUnit.SECONDS));
                var response = careRecord
                        ? request("POST", "/api/v1/babies/" + babyId + "/feedings", guardian,
                                "{\"occurredAt\":\"2026-11-08T12:00:00\",\"feedingType\":\"FORMULA\"}")
                        : request("POST", base(), guardian, "{\"reportDate\":\"2026-11-08\",\"summary\":\"manual\"}");
                assertEquals(403, response.statusCode(), response.body());
                revocation.get(5, TimeUnit.SECONDS);
            }
        }
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM daily_reports WHERE baby_id=?", Long.class, babyId));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM feeding_records WHERE baby_id=?", Long.class, babyId));
    }

    private void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }

    @Test
    void failedGenerationReleasesItsReservationForRetry() throws Exception {
        seed();
        when(generator.generate(any())).thenThrow(new DailyReportGenerationException(ErrorCode.DAILY_REPORT_GENERATION_UNAVAILABLE))
                .thenReturn("재시도 요약");
        assertEquals(503, generate(ownerId).statusCode());
        assertEquals(201, generate(ownerId).statusCode());
        verify(generator, times(2)).generate(any());
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM daily_reports WHERE baby_id=? AND status='COMPLETED'", Long.class, babyId));
    }

    @Test
    void abandonedGenerationCanBeReclaimedWithoutChangingTheSchema() throws Exception {
        seed();
        jdbc.update("INSERT INTO daily_reports(baby_id,report_date,summary,status,generated_at) VALUES (?,'2026-11-08','','PENDING',?)",
                babyId, Timestamp.valueOf(LocalDateTime.now().minusMinutes(10)));
        Long abandonedId = jdbc.queryForObject("SELECT id FROM daily_reports WHERE baby_id=?", Long.class, babyId);
        var response = generate(ownerId);
        assertEquals(201, response.statusCode(), response.body());
        assertNotEquals(abandonedId, jdbc.queryForObject("SELECT id FROM daily_reports WHERE baby_id=?", Long.class, babyId));
        assertEquals("COMPLETED", jdbc.queryForObject("SELECT status FROM daily_reports WHERE baby_id=?", String.class, babyId));
    }

    @Test
    void accessIsCheckedAgainAfterProviderReturnsAndFailedReservationIsRemoved() throws Exception {
        seed();
        when(generator.generate(any())).thenAnswer(invocation -> {
            jdbc.update("UPDATE users SET status='WITHDRAWN', withdrawn_at=CURRENT_TIMESTAMP WHERE id=?", ownerId);
            return "권한 변경 중 생성된 요약";
        });
        assertEquals(403, generate(ownerId).statusCode());
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM daily_reports WHERE baby_id=?", Long.class, babyId));
    }

    @Test
    void expiredCallerCannotCompleteOrDeleteTheNewGenerationReservation() throws Exception {
        seed();
        var enteredFirst = new CountDownLatch(1);
        var enteredSecond = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var releaseSecond = new CountDownLatch(1);
        var calls = new AtomicInteger();
        when(generator.generate(any())).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                enteredFirst.countDown();
                assertTrue(releaseFirst.await(15, TimeUnit.SECONDS));
                return "오래된 요약";
            }
            enteredSecond.countDown();
            assertTrue(releaseSecond.await(15, TimeUnit.SECONDS));
            return "새 요약";
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> generate(ownerId));
            try {
                assertTrue(enteredFirst.await(5, TimeUnit.SECONDS));
                jdbc.update("UPDATE daily_reports SET generated_at=? WHERE baby_id=?",
                        Timestamp.valueOf(LocalDateTime.now().minusMinutes(10)), babyId);
                var second = executor.submit(() -> generate(ownerId));
                assertTrue(enteredSecond.await(5, TimeUnit.SECONDS));
                releaseFirst.countDown();
                assertEquals(503, first.get(5, TimeUnit.SECONDS).statusCode());
                assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM daily_reports WHERE baby_id=?", String.class, babyId));
                releaseSecond.countDown();
                var completed = second.get(5, TimeUnit.SECONDS);
                assertEquals(201, completed.statusCode(), completed.body());
                assertEquals("새 요약", mapper.readTree(completed.body()).path("data").path("summary").asString());
            } finally {
                releaseFirst.countDown();
                releaseSecond.countDown();
            }
        }
        verify(generator, times(2)).generate(any());
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

    private String base() { return "/babies/" + babyId + "/daily-reports"; }

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
