package com.select.domain.baby.service;

import com.select.domain.auth.service.EmailVerificationService;
import com.select.domain.baby.entity.BabyMember;
import com.select.domain.baby.entity.BabyRole;
import com.select.domain.baby.repository.BabyMemberRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.baby.request.BabyCreateRequest;
import com.select.domain.baby.request.BabyUpdateRequest;
import com.select.domain.user.entity.User;
import com.select.domain.user.repository.UserRepository;
import com.select.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:baby-integration;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class BabyIntegrationTest {

    @LocalServerPort private int port;
    @Autowired private BabyService service;
    @Autowired private BabyRepository babies;
    @Autowired private BabyMemberRepository members;
    @Autowired private UserRepository users;
    @Autowired private JwtProvider jwt;
    @Autowired private ObjectMapper mapper;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private EmailVerificationService emailVerificationService;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private Long ownerId;
    private Long memberId;
    private Long outsiderId;
    private Long babyId;

    @BeforeEach
    void setUp() {
        ownerId = user("관리자");
        memberId = user("보호자");
        outsiderId = user("외부 사용자");
        babyId = service.createBaby(new BabyCreateRequest("아기", LocalDate.of(2025, 1, 15)), ownerId).babyId();
        members.saveAndFlush(BabyMember.builder().babyId(babyId).userId(memberId).role(BabyRole.MEMBER).build());
    }

    @Test
    void registrationWithoutFamilyUsesJwtIdentityAndLimitsReadsToMembers() throws Exception {
        var response = request("POST", "/babies", ownerId,
                "{\"name\":\"둘째\",\"birthDate\":\"2025-02-01\",\"userId\":" + outsiderId + "}");
        assertEquals(200, response.statusCode(), response.body());
        var body = mapper.readTree(response.body());
        assertTrue(body.path("success").asBoolean());
        long registeredId = body.path("data").path("babyId").asLong();
        assertEquals(ownerId, babies.findById(registeredId).orElseThrow().getCreatedByUserId());
        assertEquals(BabyRole.OWNER, members.findByBabyIdAndUserId(registeredId, ownerId).orElseThrow().getRole());
        assertFalse(members.existsByBabyIdAndUserId(registeredId, outsiderId));
        assertEquals(200, request("GET", "/babies/" + babyId, memberId, null).statusCode());
        var guardians = mapper.readTree(request("GET", "/babies/" + babyId + "/members", memberId, null).body());
        assertEquals(2, guardians.path("data").size());
        assertTrue(guardians.path("data").get(0).has("nickname"));
        assertEquals(2, mapper.readTree(request("GET", "/babies", ownerId, null).body()).path("data").size());
        assertEquals(0, mapper.readTree(request("GET", "/babies", outsiderId, null).body()).path("data").size());
        assertError(request("GET", "/babies/" + babyId, outsiderId, null), 403, "FORBIDDEN");
        assertError(request("GET", "/babies/" + babyId + "/members", outsiderId, null), 403, "FORBIDDEN");
        assertError(request("GET", "/babies/" + Long.MAX_VALUE, ownerId, null), 404, "NOT_FOUND");
    }

    @ParameterizedTest
    @CsvSource({"POST,", "GET,", "GET,/id", "PATCH,/id", "GET,/id/members", "DELETE,/id/members/me", "PATCH,/id/members/owner"})
    void allBabyEndpointsRequireJwt(String method, String suffix) throws Exception {
        String path = "/babies" + (suffix == null ? "" : suffix.replace("/id", "/" + babyId));
        assertError(request(method, path, null, "{\"name\":\"아기\",\"birthDate\":\"2025-01-15\",\"newOwnerUserId\":" + memberId + "}"),
                401, "UNAUTHORIZED");
    }

    @Test
    void transferAndLeavePersistRolesAndRejectIneligibleUsers() throws Exception {
        String ownerPath = "/babies/" + babyId + "/members/owner";
        String leavePath = "/babies/" + babyId + "/members/me";
        String target = "{\"newOwnerUserId\":" + memberId + "}";
        assertError(request("DELETE", leavePath, ownerId, null), 403, "FORBIDDEN");
        assertError(request("DELETE", leavePath, outsiderId, null), 403, "FORBIDDEN");
        assertError(request("PATCH", ownerPath, outsiderId, target), 403, "FORBIDDEN");
        assertError(request("PATCH", ownerPath, memberId, "{\"newOwnerUserId\":" + ownerId + "}"), 403, "FORBIDDEN");
        assertError(request("PATCH", ownerPath, ownerId, "{\"newOwnerUserId\":" + outsiderId + "}"), 403, "FORBIDDEN");
        assertError(request("PATCH", ownerPath, ownerId, "{\"newOwnerUserId\":" + ownerId + "}"), 400, "VALIDATION_ERROR");
        assertEquals(200, request("PATCH", ownerPath, ownerId, target).statusCode());
        assertEquals(BabyRole.MEMBER, members.findByBabyIdAndUserId(babyId, ownerId).orElseThrow().getRole());
        assertEquals(BabyRole.OWNER, members.findByBabyIdAndUserId(babyId, memberId).orElseThrow().getRole());
        assertError(request("DELETE", leavePath, memberId, null), 403, "FORBIDDEN");
        assertEquals(200, request("DELETE", leavePath, ownerId, null).statusCode());
        assertFalse(members.existsByBabyIdAndUserId(babyId, ownerId));
        assertTrue(babies.existsById(babyId));
        assertError(request("GET", "/babies/" + babyId, ownerId, null), 403, "FORBIDDEN");
        assertError(request("DELETE", leavePath, ownerId, null), 403, "FORBIDDEN");
    }

    @Test
    void concurrentTransfersCannotCreateTwoOwners() throws Exception {
        members.saveAndFlush(BabyMember.builder().babyId(babyId).userId(outsiderId).role(BabyRole.MEMBER).build());
        assertSerializedRejection(() -> service.transferOwnership(babyId, ownerId, memberId),
                () -> service.transferOwnership(babyId, ownerId, outsiderId));
        assertEquals(List.of(memberId), ownerIds());
    }

    @Test
    void leavingTargetCannotBecomeOwnerInConcurrentTransfer() throws Exception {
        assertSerializedRejection(() -> service.leaveBaby(babyId, memberId),
                () -> service.transferOwnership(babyId, ownerId, memberId));
        assertEquals(List.of(ownerId), ownerIds());
        assertFalse(members.existsByBabyIdAndUserId(babyId, memberId));
    }

    @Test
    void newOwnerCannotLeaveDuringConcurrentTransfer() throws Exception {
        assertSerializedRejection(() -> service.transferOwnership(babyId, ownerId, memberId),
                () -> service.leaveBaby(babyId, memberId));
        assertEquals(List.of(memberId), ownerIds());
    }

    @Test
    void ownerCanUpdateBothFieldsAndReadsReflectChanges() throws Exception {
        var original = babies.findById(babyId).orElseThrow();
        var response = request("PATCH", "/babies/" + babyId, ownerId,
                "{\"name\":\"새 이름\",\"birthDate\":\"2025-02-02\",\"userId\":" + outsiderId + "}");
        assertEquals(200, response.statusCode(), response.body());
        var body = mapper.readTree(response.body());
        assertTrue(body.path("success").asBoolean());
        assertEquals("아기 정보 수정에 성공했습니다.", body.path("message").asString());
        assertEquals(babyId.longValue(), body.path("data").path("babyId").asLong());
        assertEquals("새 이름", body.path("data").path("name").asString());
        assertEquals("2025-02-02", body.path("data").path("birthDate").asString());
        assertFalse(body.has("error"));
        var saved = babies.findById(babyId).orElseThrow();
        assertEquals(original.getCreatedByUserId(), saved.getCreatedByUserId());
        assertEquals(original.getCreatedAt(), saved.getCreatedAt());
        assertEquals(2, members.findByBabyId(babyId).size());
        var detail = mapper.readTree(request("GET", "/babies/" + babyId, memberId, null).body());
        assertEquals("새 이름", detail.path("data").path("name").asString());
        assertEquals("2025-02-02", detail.path("data").path("birthDate").asString());
        var list = mapper.readTree(request("GET", "/babies", ownerId, null).body());
        assertEquals("새 이름", list.path("data").get(0).path("name").asString());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{\"name\":\"이름만 변경\"}|이름만 변경|2025-01-15",
            "{\"birthDate\":\"2025-03-01\"}|아기|2025-03-01",
            "{}|아기|2025-01-15"
    })
    void patchPreservesOmittedFields(String json, String name, String birthDate) throws Exception {
        var response = request("PATCH", "/babies/" + babyId, ownerId, json);
        assertEquals(200, response.statusCode(), response.body());
        var saved = babies.findById(babyId).orElseThrow();
        assertEquals(name, saved.getName());
        assertEquals(LocalDate.parse(birthDate), saved.getBirthDate());
    }

    @Test
    void patchAcceptsMaximumNameLengthAndTodaysBirthDate() throws Exception {
        String name = "가".repeat(50);
        LocalDate today = LocalDate.now();
        var response = request("PATCH", "/babies/" + babyId, ownerId,
                "{\"name\":\"" + name + "\",\"birthDate\":\"" + today + "\"}");
        assertEquals(200, response.statusCode(), response.body());
        var saved = babies.findById(babyId).orElseThrow();
        assertEquals(name, saved.getName());
        assertEquals(today, saved.getBirthDate());
    }

    @ParameterizedTest
    @MethodSource("invalidUpdates")
    void invalidPatchReturns400WithoutChangingEitherField(String json) throws Exception {
        var response = request("PATCH", "/babies/" + babyId, ownerId, json);
        assertEquals(400, response.statusCode(), response.body());
        var body = mapper.readTree(response.body());
        assertFalse(body.path("success").asBoolean());
        assertTrue(body.path("data").isNull());
        assertTrue(List.of("VALIDATION_ERROR", "INVALID_REQUEST_BODY")
                .contains(body.path("error").path("code").asString()));
        var saved = babies.findById(babyId).orElseThrow();
        assertEquals("아기", saved.getName());
        assertEquals(LocalDate.of(2025, 1, 15), saved.getBirthDate());
    }

    static Stream<String> invalidUpdates() {
        return Stream.of(
                "{\"name\":\"\",\"birthDate\":\"2025-02-02\"}",
                "{\"name\":\"   \"}",
                "{\"name\":\"　\"}",
                "{\"name\":\"" + "가".repeat(51) + "\"}",
                "{\"name\":\"변경되면 안 됨\",\"birthDate\":\"" + LocalDate.now().plusDays(1) + "\"}",
                "{\"birthDate\":\"invalid-date\"}",
                "{\"birthDate\":\"2025-02-30\"}",
                "{\"name\":null}",
                "{\"birthDate\":null}",
                "{\"name\":42}",
                "{\"birthDate\":42}",
                "[]",
                "null",
                "{"
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void memberAndOutsiderCannotUpdateBaby(boolean member) throws Exception {
        assertError(request("PATCH", "/babies/" + babyId, member ? memberId : outsiderId,
                "{\"name\":\"권한 없는 수정\",\"birthDate\":\"2025-02-02\"}"), 403, "FORBIDDEN");
        var saved = babies.findById(babyId).orElseThrow();
        assertEquals("아기", saved.getName());
        assertEquals(LocalDate.of(2025, 1, 15), saved.getBirthDate());
    }

    @Test
    void patchMissingBabyReturns404() throws Exception {
        assertError(request("PATCH", "/babies/" + Long.MAX_VALUE, ownerId,
                "{\"name\":\"새 이름\"}"), 404, "NOT_FOUND");
    }

    @Test
    void updateUsesCurrentOwnerAfterTransfer() throws Exception {
        service.transferOwnership(babyId, ownerId, memberId);
        assertError(request("PATCH", "/babies/" + babyId, ownerId, "{\"name\":\"이전 관리자\"}"), 403, "FORBIDDEN");
        assertEquals(200, request("PATCH", "/babies/" + babyId, memberId, "{\"name\":\"새 관리자\"}").statusCode());
        assertEquals("새 관리자", babies.findById(babyId).orElseThrow().getName());
    }

    @Test
    void updateWaitsForConcurrentTransferBeforeCheckingOwner() throws Exception {
        assertSerializedRejection(() -> service.transferOwnership(babyId, ownerId, memberId),
                () -> service.updateBaby(babyId, ownerId, new BabyUpdateRequest("이전 관리자", null)));
        assertEquals("아기", babies.findById(babyId).orElseThrow().getName());
    }

    private void assertSerializedRejection(Runnable firstAction, Runnable secondAction) throws Exception {
        CountDownLatch firstChanged = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                firstAction.run();
                firstChanged.countDown();
                try {
                    assertTrue(releaseFirst.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }));
            try {
                assertTrue(firstChanged.await(5, TimeUnit.SECONDS));
                var second = executor.submit(() -> {
                    secondStarted.countDown();
                    return assertThrows(AccessDeniedException.class, secondAction::run);
                });
                assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
                // The second authorization check must wait for the first transaction's commit.
                assertThrows(TimeoutException.class, () -> second.get(500, TimeUnit.MILLISECONDS));
                releaseFirst.countDown();
                first.get(5, TimeUnit.SECONDS);
                assertNotNull(second.get(5, TimeUnit.SECONDS));
            } finally {
                releaseFirst.countDown();
            }
        }
    }

    private List<Long> ownerIds() {
        return members.findByBabyId(babyId).stream().filter(member -> member.getRole() == BabyRole.OWNER)
                .map(BabyMember::getUserId).toList();
    }

    private Long user(String nickname) {
        return users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.invalid")
                .passwordHash("test-only-hash").nickname(nickname).build()).getId();
    }

    private HttpResponse<String> request(String method, String path, Long userId, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(10));
        if (userId != null) {
            builder.header("Authorization", "Bearer " + jwt.createAccessToken(userId.toString()));
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertError(HttpResponse<String> response, int status, String code) {
        assertEquals(status, response.statusCode(), response.body());
        var body = mapper.readTree(response.body());
        assertFalse(body.path("success").asBoolean());
        assertTrue(body.path("data").isNull());
        assertEquals(code, body.path("error").path("code").asString());
        assertTrue(body.path("error").path("details").isArray());
    }
}
