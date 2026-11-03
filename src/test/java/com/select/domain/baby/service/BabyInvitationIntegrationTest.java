package com.select.domain.baby.service;

import com.select.domain.auth.mail.EmailSender;
import com.select.domain.baby.entity.*;
import com.select.domain.baby.exception.BabyInvitationException;
import com.select.domain.baby.repository.*;
import com.select.domain.baby.request.BabyCreateRequest;
import com.select.domain.user.entity.User;
import com.select.domain.user.repository.UserRepository;
import com.select.global.exception.ErrorCode;
import com.select.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.MailSendException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:baby-invitation-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop",
        "baby.invitation.link-base-url=https://app.babi.test/invite"
})
class BabyInvitationIntegrationTest {
    @LocalServerPort private int port;
    @Autowired private BabyInvitationWorkflow workflow;
    @Autowired private BabyService babyService;
    @Autowired private BabyRepository babies;
    @Autowired private BabyMemberRepository members;
    @Autowired private BabyInvitationCodeRepository codes;
    @Autowired private AllocatedBabyInvitationCodeRepository allocatedCodes;
    @Autowired private BabyEmailInvitationRepository invitations;
    @Autowired private UserRepository users;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JwtProvider jwt;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private EmailSender sender;
    @MockitoBean private InvitationRateLimiter limiter;
    @MockitoSpyBean private InvitationCodeGenerator generator;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private Long ownerId;
    private Long guestId;
    private Long outsiderId;
    private Long babyId;
    private String email;

    @BeforeEach
    void setUp() {
        ownerId = user("owner");
        guestId = user("guest");
        outsiderId = user("outsider");
        email = users.findById(guestId).orElseThrow().getEmail();
        babyId = babyService.createBaby(new BabyCreateRequest("아기", LocalDate.of(2025, 1, 1)), ownerId).babyId();
    }

    @Test
    void codeIsGeneratedOnceAndPersistedWithoutEmailExpiry() {
        Instant before = Instant.now();
        String first = workflow.getCode(babyId, ownerId).code();
        Instant expiry = codes.findById(babyId).orElseThrow().getExpiresAt();
        assertFalse(expiry.isBefore(before.plus(Duration.ofDays(14))));
        assertTrue(expiry.isBefore(Instant.now().plus(Duration.ofDays(14)).plusSeconds(1)));
        assertTrue(first.matches("[A-Za-z0-9]{6}"));
        assertEquals(first, codes.findById(babyId).orElseThrow().value());
        assertEquals(first, workflow.getCode(babyId, ownerId).code());
        verifyNoInteractions(limiter);
        workflow.send(babyId, ownerId, email);
        expireInvitation();
        assertEquals(first, workflow.getCode(babyId, ownerId).code());
    }

    @Test
    void codeExpiresAtExactFourteenDayBoundary() {
        BabyInvitationCode code = new BabyInvitationCode(babyId, "AbC123");
        assertTrue(code.usable(code.getExpiresAt().minusNanos(1)));
        assertFalse(code.usable(code.getExpiresAt()));
        assertFalse(code.usable(code.getExpiresAt().plusNanos(1)));
    }

    @Test
    void expiredCodeRequiresOwnerReissueAndCannotBeUsed() throws Exception {
        workflow.send(babyId, ownerId, email);
        String old = workflow.getCode(babyId, ownerId).code();
        expireCode();
        long allocations = allocatedCodes.count();
        var lookupError = assertThrows(BabyInvitationException.class, () -> workflow.getCode(babyId, ownerId));
        assertEquals(ErrorCode.INVITATION_CODE_EXPIRED, lookupError.getErrorCode());
        var sendError = assertThrows(BabyInvitationException.class, () -> workflow.send(babyId, ownerId, email));
        assertEquals(ErrorCode.INVITATION_CODE_EXPIRED, sendError.getErrorCode());
        assertError(request("GET", "/babies/" + babyId + "/invitation-code", ownerId, null),
                409, "INVITATION_CODE_EXPIRED");
        assertEquals(old, codes.findById(babyId).orElseThrow().value());
        assertEquals(allocations, allocatedCodes.count());
        assertInvalid(() -> workflow.inspect(old, guestId));
        assertInvalid(() -> workflow.accept(old, guestId));
        verify(sender, times(1)).send(anyString(), anyString(), anyString());
        String replacement = workflow.rotate(babyId, ownerId).code();
        assertNotEquals(old, replacement);
        assertEquals(BabyEmailInvitation.Status.REVOKED, invitation().getStatus());
        assertInvalid(() -> workflow.accept(old, guestId));
        workflow.send(babyId, ownerId, email);
        workflow.accept(replacement, guestId);
        assertEquals(BabyRole.MEMBER, members.findByBabyIdAndUserId(babyId, guestId).orElseThrow().getRole());
    }

    @Test
    void multipleRecipientsUseSameCodeAndAcceptIndependently() {
        String otherEmail = users.findById(outsiderId).orElseThrow().getEmail();
        workflow.send(babyId, ownerId, email);
        String value = workflow.getCode(babyId, ownerId).code();
        workflow.send(babyId, ownerId, otherEmail);
        verify(sender).send(eq(email), anyString(), contains(value));
        verify(sender).send(eq(otherEmail), anyString(), contains(value));
        workflow.accept(value, guestId);
        workflow.accept(value, outsiderId);
        assertEquals(BabyRole.MEMBER, members.findByBabyIdAndUserId(babyId, guestId).orElseThrow().getRole());
        assertEquals(BabyRole.MEMBER, members.findByBabyIdAndUserId(babyId, outsiderId).orElseThrow().getRole());
    }

    @Test
    void codeUniquenessIsCaseSensitiveAndEnforcedByDatabase() {
        Long secondBaby = anotherBaby();
        doReturn("AbC123", "aBc123").when(generator).generate();
        assertEquals("AbC123", workflow.getCode(babyId, ownerId).code());
        assertEquals("aBc123", workflow.getCode(secondBaby, ownerId).code());
        Long thirdBaby = anotherBaby();
        assertThrows(DataIntegrityViolationException.class,
                () -> codes.saveAndFlush(new BabyInvitationCode(thirdBaby, "AbC123")));
    }

    @Test
    void collisionChoosesAnotherCodeWithoutReplacingExistingBabyCode() {
        Long second = anotherBaby();
        doReturn("XyZ987", "XyZ987", "ZyX987").when(generator).generate();
        assertEquals("XyZ987", workflow.getCode(babyId, ownerId).code());
        assertEquals("ZyX987", workflow.getCode(second, ownerId).code());
        assertEquals("XyZ987", workflow.getCode(babyId, ownerId).code());
    }

    @Test
    void concurrentFirstCodeRequestsReturnSamePersistedCode() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            var a = executor.submit(() -> { start.await(); return workflow.getCode(babyId, ownerId).code(); });
            var b = executor.submit(() -> { start.await(); return workflow.getCode(babyId, ownerId).code(); });
            start.countDown();
            assertEquals(a.get(5, TimeUnit.SECONDS), b.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void differentBabiesRacingForSameGeneratedCodeRemainUnique() throws Exception {
        Long otherBaby = anotherBaby();
        CountDownLatch generated = new CountDownLatch(2);
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (attempts.incrementAndGet() <= 2) {
                generated.countDown();
                assertTrue(generated.await(5, TimeUnit.SECONDS));
                return "Race12";
            }
            return "Safe34";
        }).when(generator).generate();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> workflow.getCode(babyId, ownerId).code());
            var b = executor.submit(() -> workflow.getCode(otherBaby, ownerId).code());
            assertNotEquals(a.get(8, TimeUnit.SECONDS), b.get(8, TimeUnit.SECONDS));
        }
        assertNotEquals(codes.findById(babyId).orElseThrow().value(), codes.findById(otherBaby).orElseThrow().value());
    }

    @Test
    void codeIsReadFromStorageByNewWorkflowInstance() {
        String previous = workflow.getCode(babyId, ownerId).code();
        var restarted = new BabyInvitationWorkflow(babies, members, codes, allocatedCodes, invitations, users,
                new BabyInvitationService(members), new InvitationCodeGenerator(), limiter,
                new InvitationLinkFactory("https://app.babi.test/invite"), sender, transactionManager);
        assertEquals(previous, restarted.getCode(babyId, ownerId).code());
    }

    @Test
    void retiredCodesCannotBeAssignedAgainToAnyBaby() {
        doReturn("Hist12", "Hist34", "Hist12", "Hist56").when(generator).generate();
        assertEquals("Hist12", workflow.getCode(babyId, ownerId).code());
        assertEquals("Hist34", workflow.rotate(babyId, ownerId).code());
        assertEquals("Hist56", workflow.getCode(anotherBaby(), ownerId).code());
        assertTrue(allocatedCodes.existsByCode("Hist12".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void ownerMailContainsHttpsLinkAndCodeAndDuplicateSendKeepsExpiry() {
        workflow.send(babyId, ownerId, "  " + email.toUpperCase(Locale.ROOT) + "  ");
        var first = invitation();
        assertEquals(Duration.ofDays(14), Duration.between(first.getSentAt(), first.getExpiresAt()));
        String value = workflow.getCode(babyId, ownerId).code();
        workflow.send(babyId, ownerId, email);
        var second = invitation();
        assertEquals(first.getId(), second.getId());
        assertEquals(first.getExpiresAt(), second.getExpiresAt());
        assertEquals(value, workflow.getCode(babyId, ownerId).code());
        verify(sender, times(2)).send(eq(email), eq("[BABI] 아기 보호자 초대"), argThat(body ->
                body.contains(value) && body.contains("https://app.babi.test/invite?code=" + value)
                        && !body.contains(email) && !body.contains("Bearer")));
    }

    @Test
    void expiredInvitationRenewsFourteenDaysWithoutRotatingCode() {
        workflow.send(babyId, ownerId, email);
        String value = workflow.getCode(babyId, ownerId).code();
        expireInvitation();
        var before = invitation();
        workflow.send(babyId, ownerId, email);
        var after = invitation();
        assertEquals(before.getId(), after.getId());
        assertTrue(after.getExpiresAt().isAfter(Instant.now().plus(Duration.ofDays(13))));
        assertEquals(Duration.ofDays(14), Duration.between(after.getSentAt(), after.getExpiresAt()));
        assertEquals(value, workflow.getCode(babyId, ownerId).code());
    }

    @Test
    void reissueInvalidatesOldCodeAndPendingInvitationsUntilResent() {
        workflow.send(babyId, ownerId, email);
        String old = workflow.getCode(babyId, ownerId).code();
        String replacement = workflow.rotate(babyId, ownerId).code();
        assertNotEquals(old, replacement);
        assertEquals(BabyEmailInvitation.Status.REVOKED, invitation().getStatus());
        assertInvalid(() -> workflow.accept(old, guestId));
        assertInvalid(() -> workflow.accept(replacement, guestId));
        workflow.send(babyId, ownerId, email);
        assertEquals(babyId, workflow.accept(replacement, guestId).babyId());
    }

    @Test
    void wrongEmailExpiredAndUsedInvitationsCannotBeAccepted() {
        workflow.send(babyId, ownerId, email);
        String value = workflow.getCode(babyId, ownerId).code();
        assertInvalid(() -> workflow.inspect(value, outsiderId));
        assertInvalid(() -> workflow.accept(value, outsiderId));
        expireInvitation();
        assertInvalid(() -> workflow.accept(value, guestId));
        workflow.send(babyId, ownerId, email);
        assertEquals(babyId, workflow.inspect(value, guestId).babyId());
        assertEquals(babyId, workflow.accept(value, guestId).babyId());
        assertEquals(BabyRole.MEMBER, members.findByBabyIdAndUserId(babyId, guestId).orElseThrow().getRole());
        assertEquals(BabyEmailInvitation.Status.ACCEPTED, invitation().getStatus());
        assertInvalid(() -> workflow.accept(value, guestId));
        assertEquals(2, members.findByBabyId(babyId).size());
    }

    @Test
    void invitationCanBeSentBeforeAccountExists() {
        String newEmail = UUID.randomUUID() + "@example.invalid";
        workflow.send(babyId, ownerId, newEmail);
        Long newUserId = users.saveAndFlush(User.builder().email(newEmail).passwordHash("test-only")
                .nickname("new-user").build()).getId();
        workflow.accept(workflow.getCode(babyId, ownerId).code(), newUserId);
        assertTrue(members.existsByBabyIdAndUserId(babyId, newUserId));
    }

    @Test
    void selfAndExistingMemberInvitationsAreRejectedBeforeMail() {
        var self = assertThrows(BabyInvitationException.class,
                () -> workflow.send(babyId, ownerId, users.findById(ownerId).orElseThrow().getEmail()));
        assertEquals(ErrorCode.INVITATION_SELF, self.getErrorCode());
        members.saveAndFlush(BabyMember.builder().babyId(babyId).userId(guestId).role(BabyRole.MEMBER).build());
        var duplicate = assertThrows(BabyInvitationException.class, () -> workflow.send(babyId, ownerId, email));
        assertEquals(ErrorCode.INVITATION_ALREADY_MEMBER, duplicate.getErrorCode());
        verifyNoInteractions(sender);
        assertTrue(codes.findById(babyId).isEmpty());
    }

    @Test
    void firstMailFailureRollsBackInvitationAndNewCode() {
        doThrow(new MailSendException("private address and body")).when(sender).send(anyString(), anyString(), anyString());
        var error = assertThrows(BabyInvitationException.class, () -> workflow.send(babyId, ownerId, email));
        assertEquals(ErrorCode.INVITATION_SEND_FAILED, error.getErrorCode());
        assertNull(error.getCause());
        assertTrue(codes.findById(babyId).isEmpty());
        assertTrue(invitations.findByBabyId(babyId).isEmpty());
    }

    @Test
    void failedResendPreservesPreviousExpiredInvitationAndCode() {
        workflow.send(babyId, ownerId, email);
        String value = workflow.getCode(babyId, ownerId).code();
        expireInvitation();
        Instant previousExpiry = invitation().getExpiresAt();
        doThrow(new MailSendException("test failure")).when(sender).send(anyString(), anyString(), anyString());
        assertThrows(BabyInvitationException.class, () -> workflow.send(babyId, ownerId, email));
        assertEquals(previousExpiry, invitation().getExpiresAt());
        assertEquals(value, workflow.getCode(babyId, ownerId).code());
        assertInvalid(() -> workflow.accept(value, guestId));
    }

    @Test
    void failedReissueRollsBackCodeRetirementAndRevocation() {
        workflow.send(babyId, ownerId, email);
        String old = workflow.getCode(babyId, ownerId).code();
        long allocationsBefore = allocatedCodes.count();
        doThrow(new BabyInvitationException(ErrorCode.INVITATION_RATE_LIMITED)).when(limiter).rotate(ownerId);
        assertThrows(BabyInvitationException.class, () -> workflow.rotate(babyId, ownerId));
        assertEquals(old, workflow.getCode(babyId, ownerId).code());
        assertEquals(allocationsBefore, allocatedCodes.count());
        assertEquals(BabyEmailInvitation.Status.PENDING, invitation().getStatus());
        workflow.accept(old, guestId);
        assertTrue(members.existsByBabyIdAndUserId(babyId, guestId));
    }

    @Test
    void sendRateLimitFailureDoesNotLeaveNewCodeOrInvitation() {
        doThrow(new BabyInvitationException(ErrorCode.INVITATION_RATE_LIMITED))
                .when(limiter).send(ownerId, babyId, email);
        assertThrows(BabyInvitationException.class, () -> workflow.send(babyId, ownerId, email));
        assertTrue(codes.findById(babyId).isEmpty());
        assertTrue(invitations.findByBabyId(babyId).isEmpty());
        verifyNoInteractions(sender);
    }

    @Test
    void concurrentAcceptRegistersExactlyOneMember() throws Exception {
        workflow.send(babyId, ownerId, email);
        String value = workflow.getCode(babyId, ownerId).code();
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        Runnable accept = () -> {
            try {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                workflow.accept(value, guestId);
                success.incrementAndGet();
            } catch (BabyInvitationException e) {
                assertEquals(ErrorCode.INVITATION_INVALID, e.getErrorCode());
                rejected.incrementAndGet();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(accept);
            var b = executor.submit(accept);
            start.countDown();
            a.get(5, TimeUnit.SECONDS);
            b.get(5, TimeUnit.SECONDS);
        }
        assertEquals(1, success.get());
        assertEquals(1, rejected.get());
        assertEquals(2, members.findByBabyId(babyId).size());
    }

    @Test
    void concurrentInvitationsForSameEmailReuseOneRecordAndCode() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return workflow.send(babyId, ownerId, email); });
            var second = executor.submit(() -> { start.await(); return workflow.send(babyId, ownerId, email); });
            start.countDown();
            assertEquals(first.get(8, TimeUnit.SECONDS).expiresAt(), second.get(8, TimeUnit.SECONDS).expiresAt());
        }
        assertEquals(1, invitations.findByBabyId(babyId).size());
        String code = workflow.getCode(babyId, ownerId).code();
        verify(sender, times(2)).send(eq(email), anyString(), contains(code));
        workflow.accept(code, guestId);
        assertEquals(BabyRole.MEMBER, members.findByBabyIdAndUserId(babyId, guestId).orElseThrow().getRole());
    }

    @Test
    void codeWithoutEmailInvitationAndDifferentBabysCodeCannotRegisterMember() {
        workflow.send(babyId, ownerId, email);
        String invitedCode = workflow.getCode(babyId, ownerId).code();
        Long otherBaby = anotherBaby();
        String otherCode = workflow.getCode(otherBaby, ownerId).code();
        assertNotEquals(invitedCode, otherCode);
        assertInvalid(() -> workflow.accept(otherCode, guestId));
        assertFalse(members.existsByBabyIdAndUserId(otherBaby, guestId));
        assertInvalid(() -> workflow.accept("000000".equals(invitedCode) ? "999999" : "000000", guestId));
        workflow.accept(invitedCode, guestId);
        assertTrue(members.existsByBabyIdAndUserId(babyId, guestId));
        assertFalse(members.existsByBabyIdAndUserId(otherBaby, guestId));
    }

    @Test
    void caseChangedCodeIsRejectedForAnInvitedAccount() {
        String value;
        do {
            value = "Qa" + UUID.randomUUID().toString().replace("-", "").substring(0, 4);
        } while (allocatedCodes.existsByCode(value.getBytes(StandardCharsets.US_ASCII)));
        doReturn(value).when(generator).generate();
        workflow.send(babyId, ownerId, email);
        String wrongCase = "q" + value.substring(1);
        assertInvalid(() -> workflow.inspect(wrongCase, guestId));
        assertInvalid(() -> workflow.accept(wrongCase, guestId));
        workflow.accept(value, guestId);
        assertTrue(members.existsByBabyIdAndUserId(babyId, guestId));
    }

    @Test
    void transferAndLeaveUpdateWhoCanInviteAndAccessBaby() throws Exception {
        workflow.send(babyId, ownerId, email);
        String code = workflow.getCode(babyId, ownerId).code();
        workflow.accept(code, guestId);
        String base = "/babies/" + babyId;
        assertEquals(BabyRole.OWNER, members.findByBabyIdAndUserId(babyId, ownerId).orElseThrow().getRole());
        assertEquals(BabyRole.MEMBER, members.findByBabyIdAndUserId(babyId, guestId).orElseThrow().getRole());
        assertError(request("DELETE", base + "/members/me", ownerId, null), 403, "FORBIDDEN");
        assertEquals(200, request("PATCH", base + "/members/owner", ownerId,
                "{\"newOwnerUserId\":" + guestId + "}").statusCode());
        assertEquals(BabyRole.MEMBER, members.findByBabyIdAndUserId(babyId, ownerId).orElseThrow().getRole());
        assertEquals(BabyRole.OWNER, members.findByBabyIdAndUserId(babyId, guestId).orElseThrow().getRole());
        assertError(request("GET", base + "/invitation-code", ownerId, null), 403, "FORBIDDEN");
        assertEquals(200, request("GET", base + "/invitation-code", guestId, null).statusCode());
        assertEquals(200, request("POST", base + "/invitation-code/reissue", guestId, null).statusCode());
        assertEquals(200, request("POST", base + "/invitations", guestId,
                mapper.writeValueAsString(Map.of("email", users.findById(outsiderId).orElseThrow().getEmail()))).statusCode());
        assertEquals(200, request("DELETE", base + "/members/me", ownerId, null).statusCode());
        assertError(request("GET", base, ownerId, null), 403, "FORBIDDEN");
        assertError(request("DELETE", base + "/members/me", guestId, null), 403, "FORBIDDEN");
    }

    @Test
    void ownerCanUseAllInvitationEndpointsOverHttp() throws Exception {
        String base = "/babies/" + babyId;
        assertEquals(200, request("GET", base + "/invitation-code", ownerId, null).statusCode());
        assertEquals(200, request("POST", base + "/invitation-code/reissue", ownerId, null).statusCode());
        assertEquals(200, request("POST", base + "/invitations", ownerId,
                mapper.writeValueAsString(Map.of("email", email))).statusCode());
        verify(sender).send(eq(email), anyString(), anyString());
    }

    @ParameterizedTest
    @CsvSource({"GET,/1/invitation-code", "POST,/1/invitation-code/reissue", "POST,/1/invitations",
            "POST,/invitations/check", "POST,/invitations/accept"})
    void invalidJwtCannotAccessAnyInvitationEndpoint(String method, String suffix) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/babies" + suffix))
                .header("Authorization", "Bearer invalid.jwt.value").header("Content-Type", "application/json")
                .method(method, method.equals("POST") ? HttpRequest.BodyPublishers.ofString("{}") : HttpRequest.BodyPublishers.noBody());
        assertError(client.send(builder.build(), HttpResponse.BodyHandlers.ofString()), 401, "INVALID_TOKEN");
        verifyNoInteractions(sender, limiter);
    }

    @Test
    void rotationBeforeConcurrentAcceptRejectsOldCode() throws Exception {
        workflow.send(babyId, ownerId, email);
        String old = workflow.getCode(babyId, ownerId).code();
        serialized(() -> workflow.rotate(babyId, ownerId), () -> assertInvalid(() -> workflow.accept(old, guestId)));
        assertFalse(members.existsByBabyIdAndUserId(babyId, guestId));
    }

    @Test
    void acceptanceBeforeRotationKeepsCommittedMember() throws Exception {
        workflow.send(babyId, ownerId, email);
        String old = workflow.getCode(babyId, ownerId).code();
        serialized(() -> workflow.accept(old, guestId), () -> workflow.rotate(babyId, ownerId));
        assertTrue(members.existsByBabyIdAndUserId(babyId, guestId));
        assertEquals(BabyEmailInvitation.Status.ACCEPTED, invitation().getStatus());
        assertInvalid(() -> workflow.accept(old, guestId));
    }

    @Test
    void concurrentReissuesLeaveOnlyOneActiveCode() throws Exception {
        String old = workflow.getCode(babyId, ownerId).code();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> workflow.rotate(babyId, ownerId).code());
            var b = executor.submit(() -> workflow.rotate(babyId, ownerId).code());
            String first = a.get(5, TimeUnit.SECONDS);
            String second = b.get(5, TimeUnit.SECONDS);
            assertNotEquals(first, second);
            String active = workflow.getCode(babyId, ownerId).code();
            assertTrue(active.equals(first) || active.equals(second));
            assertNotEquals(old, active);
            String inactive = active.equals(first) ? second : first;
            assertTrue(codes.findBabyIdByCode(inactive.getBytes(StandardCharsets.US_ASCII)).isEmpty());
        }
    }

    @ParameterizedTest
    @CsvSource({"GET,/invitation-code", "POST,/invitation-code/reissue", "POST,/invitations"})
    void membersAndOutsidersCannotUseOwnerEndpoints(String method, String suffix) throws Exception {
        members.saveAndFlush(BabyMember.builder().babyId(babyId).userId(guestId).role(BabyRole.MEMBER).build());
        for (Long userId : List.of(guestId, outsiderId)) {
            assertError(request(method, "/babies/" + babyId + suffix, userId,
                    method.equals("POST") ? "{\"email\":\"new@example.invalid\"}" : null), 403, "FORBIDDEN");
        }
        verifyNoInteractions(sender);
    }

    @ParameterizedTest
    @CsvSource({"GET,/1/invitation-code", "POST,/1/invitation-code/reissue", "POST,/1/invitations",
            "POST,/invitations/check", "POST,/invitations/accept"})
    void allInvitationEndpointsRequireAuthentication(String method, String suffix) throws Exception {
        assertError(request(method, "/babies" + suffix, null,
                method.equals("POST") ? "{\"email\":\"test@example.invalid\",\"code\":\"AbC123\"}" : null),
                401, "UNAUTHORIZED");
        verifyNoInteractions(sender, limiter);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bad-email", "", "   ", "user@"})
    void invalidEmailsReturn400BeforeSending(String email) throws Exception {
        assertError(request("POST", "/babies/" + babyId + "/invitations", ownerId,
                mapper.writeValueAsString(Map.of("email", email))), 400, "VALIDATION_ERROR");
        verifyNoInteractions(sender, limiter);
    }

    @Test
    void checkNeverRevealsTargetEmailAndAcceptUsesJwtAccount() throws Exception {
        assertEquals(200, request("POST", "/babies/" + babyId + "/invitations", ownerId,
                mapper.writeValueAsString(Map.of("email", email))).statusCode());
        String value = workflow.getCode(babyId, ownerId).code();
        String json = mapper.writeValueAsString(Map.of("code", value, "email", email));
        var wrong = request("POST", "/babies/invitations/check", outsiderId, json);
        assertError(wrong, 400, "INVITATION_INVALID");
        assertFalse(wrong.body().contains(email));
        var own = request("POST", "/babies/invitations/check", guestId, json);
        assertEquals(200, own.statusCode(), own.body());
        assertFalse(own.body().contains(email));
        assertEquals(babyId.longValue(), mapper.readTree(own.body()).path("data").path("babyId").asLong());
        assertError(request("POST", "/babies/invitations/accept", outsiderId, json), 400, "INVITATION_INVALID");
        assertEquals(200, request("POST", "/babies/invitations/accept", guestId, json).statusCode());
    }

    @Test
    void rateLimitAndDependencyFailuresUseApiErrorsWithoutSecrets() throws Exception {
        doThrow(new BabyInvitationException(ErrorCode.INVITATION_RATE_LIMITED)).when(limiter).lookup(guestId);
        assertError(request("POST", "/babies/invitations/check", guestId, "{\"code\":\"AbC123\"}"),
                429, "INVITATION_RATE_LIMITED");
        doThrow(new MailSendException(email + " secret-code")).when(sender).send(anyString(), anyString(), anyString());
        var failed = request("POST", "/babies/" + babyId + "/invitations", ownerId,
                mapper.writeValueAsString(Map.of("email", email)));
        assertError(failed, 503, "INVITATION_SEND_FAILED");
        assertFalse(failed.body().contains(email));
        assertFalse(failed.body().contains("secret-code"));
    }

    private void serialized(Runnable firstAction, Runnable secondAction) throws Exception {
        CountDownLatch changed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                firstAction.run();
                changed.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { throw new IllegalStateException(e); }
            }));
            try {
                assertTrue(changed.await(5, TimeUnit.SECONDS));
                var second = executor.submit(() -> { started.countDown(); secondAction.run(); });
                assertTrue(started.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> second.get(300, TimeUnit.MILLISECONDS));
                release.countDown();
                first.get(5, TimeUnit.SECONDS);
                second.get(5, TimeUnit.SECONDS);
            } finally { release.countDown(); }
        }
    }

    private void assertInvalid(Runnable action) {
        assertEquals(ErrorCode.INVITATION_INVALID, assertThrows(BabyInvitationException.class, action::run).getErrorCode());
    }

    private void expireInvitation() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var existing = invitations.findForUpdate(babyId, email).orElseThrow();
            existing.renew(existing.getGeneration(), Instant.now().minus(Duration.ofDays(14)));
        });
    }

    private void expireCode() {
        jdbc.update("update baby_invitation_codes set expires_at = ? where baby_id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), babyId);
    }

    private BabyEmailInvitation invitation() {
        return invitations.findByBabyId(babyId).getFirst();
    }

    private Long user(String nickname) {
        return users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.invalid")
                .nickname(nickname).passwordHash("test-only-hash").build()).getId();
    }

    private Long anotherBaby() {
        return babyService.createBaby(new BabyCreateRequest("다른 아기", LocalDate.of(2025, 1, 1)), ownerId).babyId();
    }

    private HttpResponse<String> request(String method, String path, Long userId, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(10));
        if (userId != null) builder.header("Authorization", "Bearer " + jwt.createAccessToken(userId.toString()));
        if (body != null) builder.header("Content-Type", "application/json");
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertError(HttpResponse<String> response, int status, String code) {
        assertEquals(status, response.statusCode(), response.body());
        var body = mapper.readTree(response.body());
        assertFalse(body.path("success").asBoolean());
        assertTrue(body.path("data").isNull());
        assertEquals(code, body.path("error").path("code").asString());
    }
}
