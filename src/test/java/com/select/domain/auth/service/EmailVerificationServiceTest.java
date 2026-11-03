package com.select.domain.auth.service;

import com.select.domain.auth.exception.EmailVerificationException;
import com.select.domain.auth.mail.EmailSender;
import com.select.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Locale;
import java.time.Duration;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
@SuppressWarnings("unchecked")
class EmailVerificationServiceTest {

    private StringRedisTemplate redis;
    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        service = new EmailVerificationService(mock(EmailSender.class), redis, "test-only-secret");
    }

    @AfterEach
    void cleanTransactionContext() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @ParameterizedTest
    @CsvSource({"-1,EMAIL_CODE_EXPIRED", "-2,EMAIL_ATTEMPTS_EXCEEDED", "0,EMAIL_CODE_MISMATCH"})
    void translatesVerificationResults(long result, ErrorCode expected) {
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(result);
        assertEquals(expected, assertThrows(EmailVerificationException.class,
                () -> service.verifyCode("  USER@EXAMPLE.COM  ", "123456")).getErrorCode());
        verify(redis).execute(any(RedisScript.class), eq(List.of("email:verification:user@example.com",
                        "email:attempts:user@example.com", "email:verified:user@example.com")),
                anyString(), eq("5"), eq("600000"), eq("600000"), startsWith("proof:"));
    }

    @ParameterizedTest
    @CsvSource({"-1,EMAIL_NOT_VERIFIED", "-2,EMAIL_VERIFICATION_IN_PROGRESS", "0,EMAIL_VERIFICATION_UNAVAILABLE"})
    void reservationRejectsMissingBusyOrUnexpectedResult(long result, ErrorCode expected) {
        beginTransaction();
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString())).thenReturn(result);
        assertEquals(expected, assertThrows(EmailVerificationException.class,
                () -> service.reserveForSignup("USER@EXAMPLE.COM", "A".repeat(43))).getErrorCode());
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
    }

    @Test
    void refusesReservationOutsideTransaction() {
        assertThrows(IllegalStateException.class, () -> service.reserveForSignup("user@example.com", "A".repeat(43)));
        verifyNoInteractions(redis);
    }

    @ParameterizedTest
    @CsvSource({"0,consume", "1,rollback", "2,consume"})
    void cleanupFollowsActualTransactionCompletion(int status, String action) {
        beginTransaction();
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString())).thenReturn(1L);
        service.reserveForSignup(" USER@EXAMPLE.COM ", "A".repeat(43));
        // flush/메서드 반환 시점에는 인증을 정리하지 않는다.
        verify(redis, never()).execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString());
        TransactionSynchronization synchronization = TransactionSynchronizationManager.getSynchronizations().getFirst();
        synchronization.afterCompletion(status);
        verify(redis).execute(any(RedisScript.class), eq(List.of("email:verified:user@example.com")),
                startsWith("signup:"), eq(action), matches("proof:[0-9a-f]{64}"));
    }

    @ParameterizedTest
    @CsvSource({"0,consume", "1,rollback"})
    void cleanupFailureLogsSafeCauseWithoutChangingTransactionOutcome(int status, String action, CapturedOutput output) {
        beginTransaction();
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString())).thenReturn(1L);
        var failure = new RedisConnectionFailureException("private-command-token-secret",
                new IllegalStateException("private-password-code"));
        failure.addSuppressed(new IllegalArgumentException("private-suppressed-secret"));
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenThrow(failure);
        service.reserveForSignup("user@example.com", "A".repeat(43));
        assertDoesNotThrow(() -> TransactionSynchronizationManager.getSynchronizations().getFirst().afterCompletion(status));
        assertTrue(output.getAll().contains("cleanupId="));
        assertTrue(output.getAll().contains("action=" + action));
        assertTrue(output.getAll().contains("transactionStatus=" + status));
        assertTrue(output.getAll().contains(RedisConnectionFailureException.class.getName()));
        assertTrue(output.getAll().contains(IllegalStateException.class.getName()));
        assertTrue(output.getAll().contains("EmailVerificationServiceTest.java:"));
        assertFalse(output.getAll().contains("private-"));
        assertFalse(output.getAll().contains("user@example.com"));
        assertFalse(output.getAll().contains("A".repeat(43)));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 2})
    void cleanupDistinguishesNormalNoOpFromUnexpectedResult(long result, CapturedOutput output) {
        beginTransaction();
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString())).thenReturn(1L);
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString())).thenReturn(result);
        service.reserveForSignup("user@example.com", "A".repeat(43));
        assertDoesNotThrow(() -> TransactionSynchronizationManager.getSynchronizations().getFirst()
                .afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        assertEquals(result != 0, output.getAll().contains("정리 결과가 올바르지 않습니다"));
    }

    @Test
    void redisOutageBeforeSendDoesNotCallSmtp() {
        var values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("test outage"));
        EmailSender sender = mock(EmailSender.class);
        var verification = new EmailVerificationService(sender, redis, "test-only-secret");
        assertEquals(ErrorCode.EMAIL_VERIFICATION_UNAVAILABLE, assertThrows(EmailVerificationException.class,
                () -> verification.sendVerificationCode("user@example.com")).getErrorCode());
        verifyNoInteractions(sender);
    }

    @Test
    void redisPublicationFailureOnlyReleasesOwnCooldown() {
        var values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("test outage"));
        assertEquals(ErrorCode.EMAIL_VERIFICATION_UNAVAILABLE, assertThrows(EmailVerificationException.class,
                () -> service.sendVerificationCode("user@example.com")).getErrorCode());
        verify(redis).execute(any(RedisScript.class), eq(List.of("email:resend:user@example.com")), anyString());
        verify(redis, never()).execute(any(RedisScript.class), eq(List.of("email:verification:user@example.com")), anyString());
    }

    @Test
    void redisOutageDuringVerificationReturnsUnavailable() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("test outage"));
        assertEquals(ErrorCode.EMAIL_VERIFICATION_UNAVAILABLE, assertThrows(EmailVerificationException.class,
                () -> service.verifyCode("user@example.com", "123456")).getErrorCode());
    }

    @Test
    void unavailableRedisRejectsSignupBeforePersistence() {
        beginTransaction();
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("test outage"));
        assertEquals(ErrorCode.EMAIL_VERIFICATION_UNAVAILABLE, assertThrows(EmailVerificationException.class,
                () -> service.reserveForSignup("user@example.com", "A".repeat(43))).getErrorCode());
    }

    @Test
    void generatedCodeAlwaysUsesSixAsciiDigitsRegardlessOfServerLocale() {
        var values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString())).thenReturn(1L);
        EmailSender sender = mock(EmailSender.class);
        var verification = new EmailVerificationService(sender, redis, "test-only-secret");
        Locale previous = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.Category.FORMAT, Locale.forLanguageTag("ar"));
            verification.sendVerificationCode("user@example.com");
            var body = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(sender).send(eq("user@example.com"), anyString(), body.capture());
            assertTrue(body.getValue().matches("(?s).*인증번호는 [0-9]{6}입니다.*"));
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, previous);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "short", "token.with.jwt.dots", " AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
    void rejectsMissingOrMalformedTokenEvenWithoutMvc(String token) {
        assertEquals(ErrorCode.EMAIL_NOT_VERIFIED, assertThrows(EmailVerificationException.class,
                () -> service.reserveForSignup("user@example.com", token)).getErrorCode());
        verifyNoInteractions(redis);
    }

    @Test
    void returnsRandomTokenButSendsOnlyDigestToRedis() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1L);
        var response = service.verifyCode(" USER@EXAMPLE.COM ", "123456");
        assertEquals("user@example.com", response.email());
        assertTrue(response.verified());
        assertEquals(600, response.expiresIn());
        assertTrue(response.verificationToken().matches("[A-Za-z0-9_-]{43}"));
        var stored = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(redis).execute(any(RedisScript.class), anyList(), anyString(), anyString(), eq("600000"),
                anyString(), stored.capture());
        assertTrue(stored.getValue().matches("proof:[0-9a-f]{64}"));
        assertFalse(stored.getValue().contains(response.verificationToken()));
    }

    private void beginTransaction() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }
}
