package com.select.domain.handover;

import com.select.domain.baby.entity.BabyMember;
import com.select.domain.baby.entity.BabyRole;
import com.select.domain.baby.repository.BabyMemberRepository;
import com.select.domain.baby.request.BabyCreateRequest;
import com.select.domain.baby.service.BabyService;
import com.select.domain.handover.repository.HandoverBriefingRepository;
import com.select.domain.handover.service.HandoverBriefingGenerator;
import com.select.domain.handover.service.HandoverBriefingRateLimiter;
import com.select.domain.report.dto.response.DailyReportFacts;
import com.select.domain.user.entity.User;
import com.select.domain.user.repository.UserRepository;
import com.select.global.security.jwt.JwtProvider;
import com.select.global.security.jwt.TokenRevocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:handover-briefing;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class HandoverBriefingIntegrationTest {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    @LocalServerPort private int port;
    @Autowired private UserRepository users;
    @Autowired private BabyService babies;
    @Autowired private BabyMemberRepository members;
    @Autowired private HandoverBriefingRepository briefings;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtProvider jwt;
    @Autowired private ObjectMapper mapper;
    @MockitoBean private HandoverBriefingGenerator generator;
    @MockitoBean private HandoverBriefingRateLimiter rateLimiter;
    @MockitoBean private TokenRevocationService tokenRevocations;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private Long ownerId;
    private Long writerId;
    private Long readerId;
    private Long outsiderId;
    private Long babyId;
    private Long writerMemberId;

    @BeforeEach
    void setUp() {
        ownerId = createUser("owner");
        writerId = createUser("writer");
        readerId = createUser("reader");
        outsiderId = createUser("outsider");
        babyId = babies.createBaby(new BabyCreateRequest("아기", LocalDate.of(2026, 1, 1)), ownerId).babyId();
        writerMemberId = addMember(writerId, BabyRole.GUARDIAN, "CARE_WRITE");
        addMember(readerId, BabyRole.VIEWER, "CARE_READ");
        when(generator.generate(any())).thenReturn("다음 보호자를 위한 브리핑입니다.");
    }

    @Test
    void createsBriefingFromOnlySelectedBabysTodayFactsAndPersistsIt() throws Exception {
        LocalDate today = LocalDate.now(SEOUL_ZONE);
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        Long otherBabyId = babies.createBaby(
                new BabyCreateRequest("다른 아기", LocalDate.of(2026, 2, 1)), outsiderId).babyId();
        insertFeeding(babyId, start.minusSeconds(1), 900);
        insertFeeding(babyId, start, 120);
        insertFeeding(babyId, end, 800);
        insertFeeding(otherBabyId, start.plusHours(1), 700);
        jdbc.update("INSERT INTO sleep_records(baby_id,sleep_start_at,sleep_end_at) VALUES (?,?,?)",
                babyId, Timestamp.valueOf(start.minusHours(1)), Timestamp.valueOf(start.plusHours(1)));
        jdbc.update("INSERT INTO temperature_records(baby_id,temperature_celsius,measured_at) VALUES (?,36.5,?)",
                babyId, Timestamp.valueOf(start.plusHours(2)));
        doAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return "다음 보호자를 위한 브리핑입니다.";
        }).when(generator).generate(any());

        HttpResponse<String> response = request("POST", briefingUrl(), ownerId);

        assertStatus(200, response);
        assertEquals("다음 보호자를 위한 브리핑입니다.",
                mapper.readTree(response.body()).path("data").path("summary").asString());
        ArgumentCaptor<DailyReportFacts> facts = ArgumentCaptor.forClass(DailyReportFacts.class);
        verify(generator).generate(facts.capture());
        assertEquals(today, facts.getValue().date());
        assertEquals(1, facts.getValue().feedingCount());
        assertEquals(120, facts.getValue().totalFeedingMl());
        assertEquals(1, facts.getValue().completedSleepCount());
        assertEquals(60, facts.getValue().completedSleepMinutes());
        assertEquals(1, facts.getValue().temperatureCount());
        assertEquals(36.5, facts.getValue().averageTemperatureCelsius());
        var saved = briefings.findTopByBabyIdOrderByGeneratedAtDescIdDesc(babyId).orElseThrow();
        assertEquals("COMPLETED", saved.getStatus());
        assertEquals(today.atStartOfDay(), saved.getPeriodStart());
        assertNotNull(saved.getPeriodEnd());
    }

    @Test
    void actionSpecificPermissionsProtectGenerationAndLatestRead() throws Exception {
        insertFeeding(babyId, LocalDate.now(SEOUL_ZONE).atTime(8, 0), 100);
        assertStatus(200, request("POST", briefingUrl(), writerId));
        assertStatus(403, request("GET", briefingUrl() + "/latest", writerId));
        assertStatus(403, request("POST", briefingUrl(), readerId));
        assertStatus(200, request("GET", briefingUrl() + "/latest", readerId));
        assertStatus(403, request("POST", briefingUrl(), outsiderId));
        assertStatus(403, request("GET", briefingUrl() + "/latest", outsiderId));
        assertStatus(401, request("GET", briefingUrl() + "/latest", null));
    }

    @Test
    void noRecordsReturns404WithoutCallingAiOrSavingBriefing() throws Exception {
        HttpResponse<String> response = request("POST", briefingUrl(), ownerId);

        assertStatus(404, response);
        assertEquals("NO_DAILY_CARE_RECORD",
                mapper.readTree(response.body()).path("error").path("code").asString());
        verifyNoInteractions(generator);
        assertEquals(0L, briefings.count());
    }

    @Test
    void revokedMembershipDuringAiCallPreventsSaving() throws Exception {
        insertFeeding(babyId, LocalDate.now(SEOUL_ZONE).atTime(8, 0), 100);
        doAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            jdbc.update("UPDATE baby_members SET removed_at=CURRENT_TIMESTAMP WHERE id=?", writerMemberId);
            return "저장되면 안 되는 브리핑";
        }).when(generator).generate(any());

        assertStatus(403, request("POST", briefingUrl(), writerId));
        assertEquals(0L, briefings.count());
    }

    @Test
    void latestReadUsesIdAsTieBreakerWhenGenerationTimesAreEqual() throws Exception {
        LocalDate today = LocalDate.now(SEOUL_ZONE);
        LocalDateTime generatedAt = today.atTime(18, 0);
        insertBriefing(today, generatedAt, "첫 번째 브리핑");
        insertBriefing(today, generatedAt, "두 번째 브리핑");

        HttpResponse<String> response = request("GET", briefingUrl() + "/latest", ownerId);

        assertStatus(200, response);
        assertEquals("두 번째 브리핑", mapper.readTree(response.body()).path("data").path("summary").asString());
    }

    @Test
    void removedMemberAndOtherBabyCannotAccessBriefings() throws Exception {
        jdbc.update("UPDATE baby_members SET removed_at=CURRENT_TIMESTAMP WHERE id=?", writerMemberId);
        Long otherBabyId = babies.createBaby(
                new BabyCreateRequest("다른 아기", LocalDate.of(2026, 2, 1)), outsiderId).babyId();

        assertStatus(403, request("POST", briefingUrl(), writerId));
        assertStatus(403, request("GET", briefingUrl() + "/latest", writerId));
        assertStatus(403, request("GET",
                "/babi/api/babies/" + otherBabyId + "/handover-briefings/latest", ownerId));
        verify(generator, never()).generate(any());
    }

    private Long createUser(String nickname) {
        return users.saveAndFlush(User.builder()
                .email(UUID.randomUUID() + "@example.invalid")
                .passwordHash("unused-test-hash")
                .nickname(nickname)
                .build()).getId();
    }

    private Long addMember(Long userId, BabyRole role, String permission) {
        Long memberId = members.saveAndFlush(BabyMember.builder()
                .babyId(babyId).userId(userId).role(role).build()).getId();
        jdbc.update("INSERT INTO baby_permissions(baby_id,baby_member_id,permission) VALUES (?,?,?)",
                babyId, memberId, permission);
        return memberId;
    }

    private void insertFeeding(Long targetBabyId, LocalDateTime occurredAt, int amountMl) {
        jdbc.update("INSERT INTO feeding_records(baby_id,feeding_type,amount_ml,occurred_at) VALUES (?,'FORMULA',?,?)",
                targetBabyId, amountMl, Timestamp.valueOf(occurredAt));
    }

    private void insertBriefing(LocalDate date, LocalDateTime generatedAt, String summary) {
        jdbc.update("""
                INSERT INTO handoff_briefings
                    (baby_id,briefing_date,period_start,period_end,summary,status,generated_at)
                VALUES (?,?,?,?,?,'COMPLETED',?)
                """, babyId, date, Timestamp.valueOf(date.atStartOfDay()), Timestamp.valueOf(generatedAt),
                summary, Timestamp.valueOf(generatedAt));
    }

    private String briefingUrl() {
        return "/babi/api/babies/" + babyId + "/handover-briefings";
    }

    private HttpResponse<String> request(String method, String path, Long userId) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15));
        if (userId != null) {
            request.header("Authorization", "Bearer " + jwt.createAccessToken(userId.toString()));
        }
        return client.send(request.method(method, HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private void assertStatus(int expected, HttpResponse<String> response) {
        assertEquals(expected, response.statusCode(), response.body());
    }
}
