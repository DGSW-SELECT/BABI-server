package com.select.domain.auth.service;

import com.select.domain.auth.exception.EmailVerificationException;
import com.select.domain.auth.mail.EmailSender;
import com.select.domain.user.dto.request.SignupRequest;
import com.select.domain.user.exception.DuplicateEmailException;
import com.select.domain.user.repository.UserRepository;
import com.select.domain.user.service.UserService;
import com.select.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import com.select.global.util.EmailNormalizer;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// H2와 실제 Redis를 사용한다. MySQL/Gmail은 사용하지 않는다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "BABI_REDIS_TEST_PORT", matches = "[0-9]+")
class EmailSignupIntegrationTest {

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> System.getenv("BABI_REDIS_TEST_PORT"));
    }

    @LocalServerPort private int port;
    @Autowired private StringRedisTemplate redis;
    @Autowired private EmailVerificationService verification;
    @Autowired private UserService users;
    @Autowired private UserRepository repository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ObjectMapper mapper;
    @MockitoBean private EmailSender sender;

    private final Map<String, String> tokens = new HashMap<>();
    private final List<String> testEmails = new ArrayList<>();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    @AfterEach
    void removeOnlyThisTestsRedisKeys() {
        for (String email : testEmails) {
            redis.delete(List.of("email:verified:" + email, "email:verification:" + email,
                    "email:attempts:" + email, "email:resend:" + email));
        }
    }

    @Test
    void unverifiedAndExpiredEmailCannotSignup() throws Exception {
        String email = email();
        assertApi(post("/auth/signup", signupBody(email)), 400, "EMAIL_NOT_VERIFIED");
        verifyNoInteractions(sender);
        tokens.put(email, issueToken(email));
        redis.expire(key(email), Duration.ofMillis(1));
        Thread.sleep(20);
        assertApi(post("/auth/signup", signupBody(email)), 400, "EMAIL_NOT_VERIFIED");
        assertFalse(repository.existsByEmail(email));
        verify(sender, times(1)).send(eq(email), anyString(), anyString());
    }

    @Test
    void sendVerifySignupLoginAndDuplicateUseSameNormalizedEmail() throws Exception {
        String email = email();
        AtomicReference<String> code = new AtomicReference<>();
        doAnswer(call -> {
            assertEquals(email, call.getArgument(0));
            String body = call.getArgument(2);
            code.set(body.replaceAll("(?s).*인증번호는 ([0-9]{6})입니다.*", "$1"));
            return null;
        }).when(sender).send(anyString(), anyString(), anyString());
        String padded = "  " + email.toUpperCase(java.util.Locale.ROOT) + "  ";
        assertEquals(200, post("/auth/email/send", Map.of("email", padded)).statusCode());
        assertApi(post("/auth/email/send", Map.of("email", email)), 429, "EMAIL_RESEND_TOO_SOON");
        String wrongCode = "000000".equals(code.get()) ? "111111" : "000000";
        assertApi(post("/auth/email/verify", Map.of("email", padded, "code", wrongCode)), 400, "EMAIL_CODE_MISMATCH");
        var verifyResponse = post("/auth/email/verify", Map.of("email", padded, "code", code.get()));
        assertEquals(200, verifyResponse.statusCode());
        var data = mapper.readTree(verifyResponse.body()).path("data");
        assertEquals(email, data.path("email").asString());
        assertTrue(data.path("verified").asBoolean());
        assertEquals(600, data.path("expiresIn").asLong());
        String token = data.path("verificationToken").asString();
        tokens.put(email, token);
        String stored = redis.opsForValue().get(key(email));
        assertTrue(stored.matches("proof:[0-9a-f]{64}"));
        assertFalse(stored.contains(token));
        long ttl = redis.getExpire(key(email), TimeUnit.MILLISECONDS);
        assertTrue(ttl > 0 && ttl <= 600000);
        assertEquals(201, post("/auth/signup", signupBody(padded)).statusCode());
        assertNull(redis.opsForValue().get(key(email)));
        assertTrue(repository.existsByEmail(email));
        var login = post("/auth/login", Map.of("email", padded, "password", "Test1234!"));
        assertEquals(200, login.statusCode());
        assertFalse(mapper.readTree(login.body()).path("data").path("accessToken").asString().isBlank());
        assertApi(post("/auth/signup", signupBody(email)), 409, "DUPLICATE_EMAIL");
        assertApi(post("/auth/email/verify", Map.of("email", email, "code", code.get())), 400, "EMAIL_CODE_EXPIRED");
        verify(sender, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    void enforcesAttemptLimitWithoutGmail() {
        String email = email();
        AtomicReference<String> code = new AtomicReference<>();
        doAnswer(call -> {
            code.set(((String) call.getArgument(2)).replaceAll("(?s).*인증번호는 ([0-9]{6})입니다.*", "$1"));
            return null;
        }).when(sender).send(anyString(), anyString(), anyString());
        verification.sendVerificationCode(email);
        String wrong = "000000".equals(code.get()) ? "111111" : "000000";
        for (int i = 0; i < 4; i++) {
            assertEquals(ErrorCode.EMAIL_CODE_MISMATCH, assertThrows(EmailVerificationException.class,
                    () -> verification.verifyCode(email, wrong)).getErrorCode());
        }
        assertEquals(ErrorCode.EMAIL_ATTEMPTS_EXCEEDED, assertThrows(EmailVerificationException.class,
                () -> verification.verifyCode(email, wrong)).getErrorCode());
        assertEquals(ErrorCode.EMAIL_ATTEMPTS_EXCEEDED, assertThrows(EmailVerificationException.class,
                () -> verification.verifyCode(email, code.get())).getErrorCode());
        assertNull(redis.opsForValue().get(key(email)));
    }

    @Test
    void flushedSignupKeepsReservationUntilOuterRollbackAndRestoresRemainingTtl() {
        String email = verifiedEmail();
        String proof = redis.opsForValue().get(key(email));
        long before = redis.getExpire(key(email), TimeUnit.MILLISECONDS);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            users.signup(signup(email));
            assertTrue(redis.opsForValue().get(key(email)).startsWith("signup:"));
            assertTrue(repository.existsByEmail(email));
            status.setRollbackOnly();
        });
        assertEquals(proof, redis.opsForValue().get(key(email)));
        long after = redis.getExpire(key(email), TimeUnit.MILLISECONDS);
        assertTrue(after > 0 && after <= before);
        assertFalse(repository.existsByEmail(email));
        users.signup(signup(email));
        assertNull(redis.opsForValue().get(key(email)));
    }

    @Test
    void failureAfterFlushBeforeCommitRestoresProofAndRollsBackUser() {
        String email = verifiedEmail();
        String proof = redis.opsForValue().get(key(email));
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    users.signup(signup(email));
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override public void beforeCommit(boolean readOnly) {
                            throw new IllegalStateException("test commit failure after flush");
                        }
                    });
                }));
        assertEquals(proof, redis.opsForValue().get(key(email)));
        assertFalse(repository.existsByEmail(email));
    }

    @Test
    void rollbackDoesNotResurrectExpiredProof() {
        String email = verifiedEmail();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            users.signup(signup(email));
            redis.expire(key(email), Duration.ofMillis(1));
            try { Thread.sleep(20); } catch (InterruptedException e) { Thread.currentThread().interrupt(); fail(e); }
            status.setRollbackOnly();
        });
        assertNull(redis.opsForValue().get(key(email)));
        assertFalse(repository.existsByEmail(email));
    }

    @Test
    void staleCompletionNeverDeletesOrRestoresOverNewProof() {
        String email = verifiedEmail();
        String proof = redis.opsForValue().get(key(email));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            users.signup(signup(email));
            redis.opsForValue().set(key(email), "new-proof-generation", Duration.ofSeconds(30));
            status.setRollbackOnly();
        });
        assertEquals("new-proof-generation", redis.opsForValue().get(key(email)));
        redis.opsForValue().set(key(email), proof, Duration.ofSeconds(30));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            users.signup(signup(email));
            redis.opsForValue().set(key(email), "new-proof-generation", Duration.ofSeconds(30));
        });
        assertEquals("new-proof-generation", redis.opsForValue().get(key(email)));
    }

    @Test
    void reservationPreventsSecondRequestWhileFirstTransactionIsOpen() throws Exception {
        String email = verifiedEmail();
        CountDownLatch reserved = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                var response = users.signup(signup(email));
                reserved.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
                return response;
            }));
            try {
                assertTrue(reserved.await(5, TimeUnit.SECONDS));
                assertEquals(ErrorCode.EMAIL_NOT_VERIFIED, assertThrows(EmailVerificationException.class,
                        () -> users.signup(new SignupRequest(email, "Test1234!", "Test1234!", "tester", "B".repeat(43))))
                        .getErrorCode());
                assertEquals(ErrorCode.EMAIL_VERIFICATION_IN_PROGRESS, assertThrows(EmailVerificationException.class,
                        () -> users.signup(signup(email))).getErrorCode());
            } finally { release.countDown(); }
            assertNotNull(first.get(5, TimeUnit.SECONDS));
        }
        assertNull(redis.opsForValue().get(key(email)));
        assertNotNull(repository.findByEmail(email).orElseThrow().getId());
    }

    @Test
    void concurrentHttpSignupsCreateExactlyOneUser() throws Exception {
        String email = verifiedEmail();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { start.await(); return post("/auth/signup", signupBody(email)); });
            var b = executor.submit(() -> { start.await(); return post("/auth/signup", signupBody(email)); });
            start.countDown();
            var responses = List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
            assertEquals(1, responses.stream().filter(r -> r.statusCode() == 201).count());
            assertEquals(1, responses.stream().filter(r -> r.statusCode() == 409).count());
        }
        assertEquals(1, repository.findAll().stream().filter(user -> user.getEmail().equals(email)).count());
        assertNull(redis.opsForValue().get(key(email)));
    }

    @Test
    void duplicateEmailFailureLeavesIndependentFreshVerificationUntouched() {
        String email = verifiedEmail();
        users.signup(signup(email));
        redis.delete("email:resend:" + email);
        tokens.put(email, issueToken(email));
        String proof = redis.opsForValue().get(key(email));
        assertThrows(DuplicateEmailException.class, () -> users.signup(signup(email)));
        assertEquals(proof, redis.opsForValue().get(key(email)));
    }

    @Test
    void flushFailureRestoresProofAndLeavesNoUser() {
        String email = verifiedEmail();
        String proof = redis.opsForValue().get(key(email));
        long before = redis.getExpire(key(email), TimeUnit.MILLISECONDS);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> users.signup(
                new SignupRequest(email, "Test1234!", "Test1234!", "x".repeat(51), tokens.get(email))));
        assertFalse(repository.existsByEmail(email));
        assertEquals(proof, redis.opsForValue().get(key(email)));
        long after = redis.getExpire(key(email), TimeUnit.MILLISECONDS);
        assertTrue(after > 0 && after <= before);
    }

    @Test
    void consumedProofCannotBeReservedAgain() {
        String email = verifiedEmail();
        var transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> verification.reserveForSignup(email, tokens.get(email)));
        assertNull(redis.opsForValue().get(key(email)));
        assertEquals(ErrorCode.EMAIL_NOT_VERIFIED, assertThrows(EmailVerificationException.class,
                () -> transaction.executeWithoutResult(status -> verification.reserveForSignup(email, tokens.get(email)))).getErrorCode());
    }

    @Test
    void failedMailReleasesOnlyItsOwnCodeAndCooldown() {
        String email = email();
        doThrow(new org.springframework.mail.MailSendException("test SMTP failure"))
                .when(sender).send(eq(email), anyString(), anyString());
        assertEquals(ErrorCode.EMAIL_SEND_FAILED, assertThrows(EmailVerificationException.class,
                () -> verification.sendVerificationCode(email)).getErrorCode());
        assertNull(redis.opsForValue().get("email:resend:" + email));
        assertNull(redis.opsForValue().get("email:verification:" + email));
        doNothing().when(sender).send(eq(email), anyString(), anyString());
        assertDoesNotThrow(() -> verification.sendVerificationCode(email));
    }

    @Test
    void missingWrongAndAnotherEmailsTokenCannotSignupOrConsumeVictimsProof() throws Exception {
        String email = verifiedEmail();
        String token = tokens.get(email);
        String proof = redis.opsForValue().get(key(email));
        var missing = new HashMap<>(signupBody(email));
        missing.remove("verificationToken");
        assertApi(post("/auth/signup", missing), 400, "VALIDATION_ERROR");
        var wrong = new HashMap<>(signupBody(email));
        wrong.put("verificationToken", "B".repeat(43));
        assertApi(post("/auth/signup", wrong), 400, "EMAIL_NOT_VERIFIED");
        String anotherEmail = email();
        var other = new HashMap<>(signupBody(anotherEmail));
        other.put("verificationToken", token);
        assertApi(post("/auth/signup", other), 400, "EMAIL_NOT_VERIFIED");
        assertFalse(repository.existsByEmail(email));
        assertFalse(repository.existsByEmail(anotherEmail));
        assertEquals(proof, redis.opsForValue().get(key(email)));
        assertEquals(201, post("/auth/signup", signupBody(email)).statusCode());
    }

    @Test
    void legacyGlobalVerifiedFlagCannotAuthorizeSignup() throws Exception {
        String email = email();
        redis.opsForValue().set(key(email), "1", Duration.ofMinutes(10));
        assertApi(post("/auth/signup", signupBody(email)), 400, "EMAIL_NOT_VERIFIED");
        assertFalse(repository.existsByEmail(email));
    }

    @Test
    void reVerificationReplacesOldTokenAndOnlyNewTokenCanSignup() throws Exception {
        String email = verifiedEmail();
        String oldToken = tokens.get(email);
        String oldProof = redis.opsForValue().get(key(email));
        // 이 테스트가 만든 키만 지워 재발송 대기 시간 경과를 대신한다.
        redis.delete("email:resend:" + email);
        String newToken = issueToken(email);
        tokens.put(email, newToken);
        assertNotEquals(oldToken, newToken);
        assertEquals(32, java.util.Base64.getUrlDecoder().decode(newToken).length);
        assertNotEquals(oldProof, redis.opsForValue().get(key(email)));
        var old = new HashMap<>(signupBody(email));
        old.put("verificationToken", oldToken);
        assertApi(post("/auth/signup", old), 400, "EMAIL_NOT_VERIFIED");
        assertEquals(201, post("/auth/signup", signupBody(email)).statusCode());
    }

    @Test
    void rollbackOfOldReservationDoesNotRestoreTokenAfterReVerification() throws Exception {
        String email = verifiedEmail();
        String oldToken = tokens.get(email);
        AtomicReference<String> newProof = new AtomicReference<>();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            users.signup(signup(email));
            redis.delete("email:resend:" + email);
            tokens.put(email, issueToken(email));
            newProof.set(redis.opsForValue().get(key(email)));
            status.setRollbackOnly();
        });
        assertEquals(newProof.get(), redis.opsForValue().get(key(email)));
        var old = new HashMap<>(signupBody(email));
        old.put("verificationToken", oldToken);
        assertApi(post("/auth/signup", old), 400, "EMAIL_NOT_VERIFIED");
        assertEquals(201, post("/auth/signup", signupBody(email)).statusCode());
    }

    @Test
    void separateClientCannotStealVictimsVerifiedEmailAndVictimCanAutoSignup() throws Exception {
        String email = email();
        AtomicReference<String> code = new AtomicReference<>();
        doAnswer(call -> {
            code.set(((String) call.getArgument(2)).replaceAll("(?s).*인증번호는 ([0-9]{6})입니다.*", "$1"));
            return null;
        }).when(sender).send(eq(email), anyString(), anyString());
        HttpClient victim = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        HttpClient attacker = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        assertEquals(200, post(victim, "/auth/email/send", Map.of("email", email)).statusCode());
        var verificationResponse = post(victim, "/auth/email/verify", Map.of("email", email, "code", code.get()));
        assertEquals(200, verificationResponse.statusCode());
        String token = mapper.readTree(verificationResponse.body()).path("data").path("verificationToken").asString();
        var attack = new HashMap<>(signupBody(email));
        attack.put("password", "Attacker123!");
        attack.put("passwordConfirm", "Attacker123!");
        attack.remove("verificationToken");
        assertApi(post(attacker, "/auth/signup", attack), 400, "VALIDATION_ERROR");
        attack.put("verificationToken", "B".repeat(43));
        assertApi(post(attacker, "/auth/signup", attack), 400, "EMAIL_NOT_VERIFIED");
        assertFalse(repository.existsByEmail(email));
        // 프론트엔드가 인증 응답 직후 보관한 입력 정보와 토큰을 제출하는 흐름.
        tokens.put(email, token);
        assertEquals(201, post(victim, "/auth/signup", signupBody(email)).statusCode());
        assertEquals(200, post(victim, "/auth/login", Map.of("email", email, "password", "Test1234!")).statusCode());
        assertApi(post(attacker, "/auth/login", Map.of("email", email, "password", "Attacker123!")),
                400, "INVALID_CREDENTIALS");
        assertNull(redis.opsForValue().get(key(email)));
    }

    @Test
    void smtpFailurePreservesPreviousCodeAndAttemptsWithRemainingTtl() {
        String email = email();
        AtomicReference<String> code = new AtomicReference<>();
        doAnswer(call -> {
            code.set(((String) call.getArgument(2)).replaceAll("(?s).*인증번호는 ([0-9]{6})입니다.*", "$1"));
            return null;
        }).when(sender).send(eq(email), anyString(), anyString());
        verification.sendVerificationCode(email);
        String codeKey = "email:verification:" + email;
        String attemptsKey = "email:attempts:" + email;
        String saved = redis.opsForValue().get(codeKey);
        String wrong = "000000".equals(code.get()) ? "111111" : "000000";
        assertThrows(EmailVerificationException.class, () -> verification.verifyCode(email, wrong));
        long before = redis.getExpire(codeKey, TimeUnit.MILLISECONDS);
        long attemptsBefore = redis.getExpire(attemptsKey, TimeUnit.MILLISECONDS);
        redis.delete("email:resend:" + email);
        doThrow(new org.springframework.mail.MailSendException("test SMTP failure"))
                .when(sender).send(eq(email), anyString(), anyString());
        assertEquals(ErrorCode.EMAIL_SEND_FAILED, assertThrows(EmailVerificationException.class,
                () -> verification.sendVerificationCode(email)).getErrorCode());
        assertEquals(saved, redis.opsForValue().get(codeKey));
        assertEquals("1", redis.opsForValue().get(attemptsKey));
        assertTrue(redis.getExpire(codeKey, TimeUnit.MILLISECONDS) > 0);
        assertTrue(redis.getExpire(codeKey, TimeUnit.MILLISECONDS) <= before);
        assertTrue(redis.getExpire(attemptsKey, TimeUnit.MILLISECONDS) > 0);
        assertTrue(redis.getExpire(attemptsKey, TimeUnit.MILLISECONDS) <= attemptsBefore);
        assertTrue(verification.verifyCode(email, code.get()).verified());
    }

    @Test
    void smtpFailureKeepsVerifiedTokenUsableForSignup() {
        String email = verifiedEmail();
        String proof = redis.opsForValue().get(key(email));
        long before = redis.getExpire(key(email), TimeUnit.MILLISECONDS);
        redis.delete("email:resend:" + email);
        doAnswer(call -> {
            assertEquals(proof, redis.opsForValue().get(key(email)));
            throw new org.springframework.mail.MailSendException("test SMTP failure");
        }).when(sender).send(eq(email), anyString(), anyString());
        assertThrows(EmailVerificationException.class, () -> verification.sendVerificationCode(email));
        assertEquals(proof, redis.opsForValue().get(key(email)));
        assertTrue(redis.getExpire(key(email), TimeUnit.MILLISECONDS) > 0);
        assertTrue(redis.getExpire(key(email), TimeUnit.MILLISECONDS) <= before);
        assertDoesNotThrow(() -> users.signup(signup(email)));
        assertNull(redis.opsForValue().get(key(email)));
    }

    @Test
    void staleSmtpCompletionCannotOverwriteNewerResend() {
        String email = email();
        AtomicReference<String> latestCode = new AtomicReference<>();
        AtomicReference<String> latestHash = new AtomicReference<>();
        AtomicReference<String> latestOwner = new AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(call -> {
            if (calls.incrementAndGet() == 1) {
                // 발송 중 60초 예약이 만료되고 다른 요청이 발송을 마친 상황.
                redis.delete("email:resend:" + email);
                verification.sendVerificationCode(email);
                latestHash.set(redis.opsForValue().get("email:verification:" + email));
                latestOwner.set(redis.opsForValue().get("email:resend:" + email));
            } else {
                latestCode.set(((String) call.getArgument(2)).replaceAll("(?s).*인증번호는 ([0-9]{6})입니다.*", "$1"));
            }
            return null;
        }).when(sender).send(eq(email), anyString(), anyString());
        assertEquals(ErrorCode.EMAIL_VERIFICATION_UNAVAILABLE, assertThrows(EmailVerificationException.class,
                () -> verification.sendVerificationCode(email)).getErrorCode());
        assertEquals(latestHash.get(), redis.opsForValue().get("email:verification:" + email));
        assertEquals(latestOwner.get(), redis.opsForValue().get("email:resend:" + email));
        assertTrue(verification.verifyCode(email, latestCode.get()).verified());
    }

    @Test
    void resendCannotResetAttemptLimitOrExtendItsWindow() {
        String email = email();
        AtomicReference<String> code = new AtomicReference<>();
        doAnswer(call -> {
            code.set(((String) call.getArgument(2)).replaceAll("(?s).*인증번호는 ([0-9]{6})입니다.*", "$1"));
            return null;
        }).when(sender).send(eq(email), anyString(), anyString());
        verification.sendVerificationCode(email);
        String wrong = "000000".equals(code.get()) ? "111111" : "000000";
        for (int i = 0; i < 4; i++) {
            assertEquals(ErrorCode.EMAIL_CODE_MISMATCH, assertThrows(EmailVerificationException.class,
                    () -> verification.verifyCode(email, wrong)).getErrorCode());
        }
        String attemptsKey = "email:attempts:" + email;
        long before = redis.getExpire(attemptsKey, TimeUnit.MILLISECONDS);
        redis.delete("email:resend:" + email);
        verification.sendVerificationCode(email);
        assertEquals("4", redis.opsForValue().get(attemptsKey));
        assertTrue(redis.getExpire(attemptsKey, TimeUnit.MILLISECONDS) > 0);
        assertTrue(redis.getExpire(attemptsKey, TimeUnit.MILLISECONDS) <= before);
        String wrongNew = "000000".equals(code.get()) ? "111111" : "000000";
        assertEquals(ErrorCode.EMAIL_ATTEMPTS_EXCEEDED, assertThrows(EmailVerificationException.class,
                () -> verification.verifyCode(email, wrongNew)).getErrorCode());
        assertEquals(ErrorCode.EMAIL_ATTEMPTS_EXCEEDED, assertThrows(EmailVerificationException.class,
                () -> verification.verifyCode(email, code.get())).getErrorCode());
        assertEquals(ErrorCode.EMAIL_RESEND_TOO_SOON, assertThrows(EmailVerificationException.class,
                () -> verification.sendVerificationCode(email)).getErrorCode());
        assertTrue(redis.getExpire("email:resend:" + email, TimeUnit.MILLISECONDS) > 0);
    }

    @Test
    void reservationRequiresExpiringProofAndKeepsRemainingTtlOnRollback() {
        String email = verifiedEmail();
        String proof = redis.opsForValue().get(key(email));
        redis.persist(key(email));
        assertEquals(ErrorCode.EMAIL_NOT_VERIFIED, assertThrows(EmailVerificationException.class,
                () -> users.signup(signup(email))).getErrorCode());
        assertEquals(proof, redis.opsForValue().get(key(email)));
        redis.expire(key(email), Duration.ofSeconds(30));
        long before = redis.getExpire(key(email), TimeUnit.MILLISECONDS);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            verification.reserveForSignup(email, tokens.get(email));
            assertTrue(redis.opsForValue().get(key(email)).startsWith("signup:"));
            long ttl = redis.getExpire(key(email), TimeUnit.MILLISECONDS);
            assertTrue(ttl > 0 && ttl <= before);
            status.setRollbackOnly();
        });
        assertEquals(proof, redis.opsForValue().get(key(email)));
        long after = redis.getExpire(key(email), TimeUnit.MILLISECONDS);
        assertTrue(after > 0 && after <= before);
        assertFalse(repository.existsByEmail(email));
    }

    private String email() {
        String email = "babi-test-" + UUID.randomUUID() + "@example.invalid";
        testEmails.add(email);
        return email;
    }

    private String verifiedEmail() {
        String email = email();
        tokens.put(email, issueToken(email));
        return email;
    }

    private String issueToken(String email) {
        AtomicReference<String> code = new AtomicReference<>();
        doAnswer(call -> {
            code.set(((String) call.getArgument(2)).replaceAll("(?s).*인증번호는 ([0-9]{6})입니다.*", "$1"));
            return null;
        }).when(sender).send(eq(email), anyString(), anyString());
        verification.sendVerificationCode(email);
        return verification.verifyCode(email, code.get()).verificationToken();
    }

    private String key(String email) { return "email:verified:" + email; }
    private SignupRequest signup(String email) { return new SignupRequest(email, "Test1234!", "Test1234!", "tester", tokens.get(email)); }
    private Map<String, String> signupBody(String email) {
        return Map.of("email", email, "password", "Test1234!", "passwordConfirm", "Test1234!", "nickname", "tester",
                "verificationToken", tokens.getOrDefault(EmailNormalizer.normalize(email), "A".repeat(43)));
    }

    private HttpResponse<String> post(String path, Map<String, String> body) throws Exception {
        return post(client, path, body);
    }

    private HttpResponse<String> post(HttpClient httpClient, String path, Map<String, String> body) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(8)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private void assertApi(HttpResponse<String> response, int status, String code) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        assertFalse(mapper.readTree(response.body()).path("success").asBoolean());
        assertEquals(code, mapper.readTree(response.body()).path("error").path("code").asString());
    }
}
