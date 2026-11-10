package com.select.domain.handover;

import com.select.domain.baby.entity.Baby;
import com.select.domain.baby.entity.BabyMember;
import com.select.domain.baby.entity.BabyRole;
import com.select.domain.baby.repository.BabyMemberRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.handover.dto.request.HandoverMemoCreateRequest;
import com.select.domain.handover.repository.HandoverMemoRepository;
import com.select.domain.handover.service.HandoverMemoService;
import com.select.domain.user.entity.User;
import com.select.domain.user.repository.UserRepository;
import com.select.global.security.jwt.JwtProvider;
import com.select.global.security.jwt.TokenRevocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:handover-memos;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class HandoverMemoIntegrationTest {

    @LocalServerPort private int port;
    @Autowired private BabyRepository babies;
    @Autowired private BabyMemberRepository members;
    @Autowired private UserRepository users;
    @Autowired private HandoverMemoRepository memos;
    @Autowired private HandoverMemoService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtProvider jwt;
    @Autowired private ObjectMapper mapper;
    @MockitoBean private TokenRevocationService revocations;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private Long ownerId;
    private Long babyId;
    private Long ownerMemberId;

    @BeforeEach
    void setUp() {
        ownerId = createUser();
        babyId = createBaby(ownerId);
        ownerMemberId = createMember(ownerId, babyId, BabyRole.OWNER, null);
    }

    @Test
    void ownerCreatesMemoAndResponseMatchesPersistedData() throws Exception {
        var response = post(babyId, token(ownerId), "{\"content\":\"낮잠 후 장난감을 정리했습니다.\"}");
        assertStatus(200, response);
        var data = mapper.readTree(response.body()).path("data");
        var saved = memos.findById(data.path("memoId").asLong()).orElseThrow();
        assertEquals(babyId, saved.getBabyId());
        assertEquals(ownerMemberId, saved.getAuthorMemberId());
        assertEquals(saved.getContent(), data.path("content").asString());
        assertEquals(ownerMemberId.longValue(), data.path("authorMemberId").asLong());
        assertNotNull(saved.getCreatedAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CARE_ALL", "CARE_WRITE"})
    void writerUsesJwtMemberInsteadOfSuppliedAuthor(String permission) throws Exception {
        Long writerId = createUser();
        Long writerMemberId = createMember(writerId, babyId, BabyRole.GUARDIAN, permission);
        var response = post(babyId, token(writerId), mapper.writeValueAsString(Map.of(
                "content", "보호자 메모", "authorMemberId", ownerMemberId, "authorId", ownerId, "groupId", 999)));
        assertStatus(200, response);
        Long memoId = mapper.readTree(response.body()).path("data").path("memoId").asLong();
        assertEquals(writerMemberId, memos.findById(memoId).orElseThrow().getAuthorMemberId());
    }

    @Test
    void rejectsMemberWithoutWritePermissionAndViewerEvenWithCareAll() throws Exception {
        Long guardianId = createUser();
        createMember(guardianId, babyId, BabyRole.GUARDIAN, "CARE_READ");
        Long viewerId = createUser();
        createMember(viewerId, babyId, BabyRole.VIEWER, "CARE_ALL");
        long before = memos.count();
        assertStatus(403, post(babyId, token(guardianId), "{\"content\":\"메모\"}"));
        assertStatus(403, post(babyId, token(viewerId), "{\"content\":\"메모\"}"));
        assertEquals(before, memos.count());
    }

    @Test
    void rejectsRemovedOwnerAndGuardianWithRemainingPermission() throws Exception {
        Long guardianId = createUser();
        Long guardianMemberId = createMember(guardianId, babyId, BabyRole.GUARDIAN, "CARE_ALL");
        jdbc.update("UPDATE baby_members SET removed_at = CURRENT_TIMESTAMP WHERE id IN (?, ?)",
                ownerMemberId, guardianMemberId);
        long before = memos.count();
        assertStatus(403, post(babyId, token(ownerId), "{\"content\":\"메모\"}"));
        assertStatus(403, post(babyId, token(guardianId), "{\"content\":\"메모\"}"));
        assertEquals(before, memos.count());
    }

    @Test
    void rejectsOtherBabyAndOtherMembersPermission() throws Exception {
        Long otherUserId = createUser();
        Long otherBabyId = createBaby(otherUserId);
        createMember(otherUserId, otherBabyId, BabyRole.OWNER, null);
        createMember(otherUserId, babyId, BabyRole.GUARDIAN, null);
        Long writerId = createUser();
        createMember(writerId, babyId, BabyRole.GUARDIAN, "CARE_ALL");
        long before = memos.count();
        assertStatus(403, post(otherBabyId, token(ownerId), "{\"content\":\"메모\"}"));
        assertStatus(403, post(babyId, token(otherUserId), "{\"content\":\"메모\"}"));
        assertStatus(403, post(Long.MAX_VALUE, token(ownerId), "{\"content\":\"메모\"}"));
        assertEquals(before, memos.count());
    }

    @Test
    void rejectsMissingInvalidAndRevokedToken() throws Exception {
        long before = memos.count();
        assertStatus(401, post(babyId, null, "{\"content\":\"메모\"}"));
        assertStatus(401, post(babyId, "invalid", "{\"content\":\"메모\"}"));
        String revoked = token(ownerId);
        when(revocations.isRevoked(revoked)).thenReturn(true);
        assertStatus(401, post(babyId, revoked, "{\"content\":\"메모\"}"));
        assertEquals(before, memos.count());
    }

    @Test
    void rejectsWithdrawnAccountInHttpAndDirectServiceCall() throws Exception {
        String issuedToken = token(ownerId);
        User user = users.findById(ownerId).orElseThrow();
        user.withdraw();
        users.saveAndFlush(user);
        long before = memos.count();
        assertStatus(401, post(babyId, issuedToken, "{\"content\":\"메모\"}"));
        assertThrows(AccessDeniedException.class,
                () -> service.createMemo(babyId, ownerId, new HandoverMemoCreateRequest("메모")));
        assertThrows(AccessDeniedException.class,
                () -> service.createMemo(babyId, Long.MAX_VALUE, new HandoverMemoCreateRequest("메모")));
        assertEquals(before, memos.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"content\":null}", "{\"content\":\"\"}", "{\"content\":\"   \"}",
            "{\"content\":\"\\t\\n\"}", "{", "null"})
    void invalidRequestDoesNotPersistMemo(String body) throws Exception {
        long before = memos.count();
        assertStatus(400, post(babyId, token(ownerId), body));
        assertEquals(before, memos.count());
    }

    @Test
    void accepts1000CharactersAndRejects1001() throws Exception {
        assertStatus(200, post(babyId, token(ownerId),
                mapper.writeValueAsString(Map.of("content", "가".repeat(1000)))));
        long before = memos.count();
        assertStatus(400, post(babyId, token(ownerId),
                mapper.writeValueAsString(Map.of("content", "가".repeat(1001)))));
        assertEquals(before, memos.count());
    }

    @Test
    void databaseRejectsAuthorFromAnotherBaby() {
        Long otherBabyId = createBaby(ownerId);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO handover_memos(baby_id, author_member_id, content) VALUES (?, ?, ?)",
                otherBabyId, ownerMemberId, "잘못된 작성자"));
    }

    @Test
    void memberRemovalPreservesExistingMemoButPreventsNewWrites() throws Exception {
        var response = post(babyId, token(ownerId), "{\"content\":\"보존할 메모\"}");
        assertStatus(200, response);
        Long memoId = mapper.readTree(response.body()).path("data").path("memoId").asLong();
        jdbc.update("UPDATE baby_members SET removed_at = CURRENT_TIMESTAMP WHERE id = ?", ownerMemberId);
        assertStatus(403, post(babyId, token(ownerId), "{\"content\":\"새 메모\"}"));
        assertTrue(memos.existsById(memoId));
    }

    private Long createUser() {
        return users.saveAndFlush(new User(UUID.randomUUID() + "@example.invalid", "test-only-hash", "보호자")).getId();
    }

    private Long createBaby(Long creatorId) {
        return babies.saveAndFlush(Baby.builder().name("테스트 아기").birthDate(LocalDate.of(2026, 1, 1))
                .createdByUserId(creatorId).build()).getId();
    }

    private Long createMember(Long userId, Long targetBabyId, BabyRole role, String permission) {
        Long memberId = members.saveAndFlush(BabyMember.builder()
                .babyId(targetBabyId).userId(userId).role(role).build()).getId();
        if (permission != null) {
            jdbc.update("INSERT INTO baby_permissions(baby_id, baby_member_id, permission) VALUES (?, ?, ?)",
                    targetBabyId, memberId, permission);
        }
        return memberId;
    }

    private String token(Long userId) {
        return jwt.createAccessToken(userId.toString());
    }

    private HttpResponse<String> post(Long targetBabyId, String token, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                        + "/babi/api/babies/" + targetBabyId + "/handover-memos"))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private void assertStatus(int expected, HttpResponse<String> response) {
        assertEquals(expected, response.statusCode(), response.body());
    }
}
