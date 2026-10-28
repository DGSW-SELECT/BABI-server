package com.select.domain.auth.service;

import com.select.domain.auth.mail.EmailSender;
import com.select.domain.auth.dto.response.EmailVerificationResponse;
import com.select.domain.auth.exception.EmailVerificationException;
import com.select.global.exception.ErrorCode;
import com.select.global.util.EmailNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    private final EmailSender emailSender;
    private final SecureRandom secureRandom = new SecureRandom();
    private final StringRedisTemplate redisTemplate;
    private static final String EMAIL_VERIFICATION_PREFIX = "email:verification:";
    private static final Duration VERIFICATION_TTL = Duration.ofMinutes(5);
    private final String verificationSecret;
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final String RESEND_PREFIX = "email:resend:";
    private static final DefaultRedisScript<Long> DELETE_IF_MATCHES_SCRIPT;
    private static final DefaultRedisScript<Long> VERIFY_CODE_SCRIPT;
    private static final String ATTEMPTS_PREFIX = "email:attempts:";
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final String VERIFIED_PREFIX = "email:verified:";
    private static final Duration ATTEMPTS_TTL = Duration.ofMinutes(10);
    private static final Duration VERIFIED_TTL = Duration.ofMinutes(10);
    private static final DefaultRedisScript<Long> SAVE_CODE_SCRIPT;

    private static final DefaultRedisScript<Long> RESERVE_SIGNUP_SCRIPT = new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            local ttl = redis.call('PTTL', KEYS[1])
            if not value or ttl <= 0 then
                return -1
            end
            if value == 'proof:' .. ARGV[1] then
                redis.call('SET', KEYS[1], ARGV[2], 'KEEPTTL')
                return 1
            end
            local reservationPrefix = 'signup:' .. ARGV[1] .. ':'
            if string.sub(value, 1, string.len(reservationPrefix)) == reservationPrefix then
                return -2
            end
            return -1
            """, Long.class);

    private static final DefaultRedisScript<Long> COMPLETE_SIGNUP_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then
                return 0
            end
            if ARGV[2] == 'rollback' and redis.call('PTTL', KEYS[1]) > 0 then
                redis.call('SET', KEYS[1], ARGV[3], 'KEEPTTL')
                return 1
            end
            return redis.call('DEL', KEYS[1])
            """, Long.class);

    static {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText("""
                if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
                else
                    return 0
                end
                """);
        script.setResultType(Long.class);
        DELETE_IF_MATCHES_SCRIPT = script;
    }

    static {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText("""
            local savedHash = redis.call('GET', KEYS[1])
            if not savedHash then
                return -1
            end
            local attempts = tonumber(redis.call('GET', KEYS[2]) or '0')
            local maxAttempts = tonumber(ARGV[2])
            if attempts >= maxAttempts then
                return -2
            end
            if savedHash ~= ARGV[1] then
                local newAttempts = redis.call('INCR', KEYS[2])
                if newAttempts == 1 then
                    redis.call('PEXPIRE', KEYS[2], ARGV[4])
                end
                if newAttempts >= maxAttempts then
                    return -2
                end
                return 0
            end
            redis.call('SET', KEYS[3], ARGV[5], 'PX', ARGV[3])
            redis.call('DEL', KEYS[1])
            return 1
            """);
        script.setResultType(Long.class);
        VERIFY_CODE_SCRIPT = script;
    }

    static {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText("""
            -- A slow SMTP request must not overwrite a newer resend's state.
            if redis.call('GET', KEYS[3]) ~= ARGV[3] or redis.call('PTTL', KEYS[3]) <= 0 then
                return 0
            end
            redis.call('DEL', KEYS[2])
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
            return 1
            """);
        script.setResultType(Long.class);
        SAVE_CODE_SCRIPT = script;
    }

    public EmailVerificationService(
            EmailSender emailSender,
            StringRedisTemplate redisTemplate,
            @Value("${email.verification.secret}") String verificationSecret
    ) {
        this.emailSender = emailSender;
        this.redisTemplate = redisTemplate;
        this.verificationSecret = verificationSecret;
    }

    private String generateCode() {
        int number = secureRandom.nextInt(1_000_000);
        return String.format(Locale.ROOT, "%06d", number);
    }

    private String generateKey(String email) {
        return EMAIL_VERIFICATION_PREFIX + email;
    }

    public void sendVerificationCode(String email) {
        email = EmailNormalizer.normalize(email);
        String resendKey = RESEND_PREFIX + email;
        String requestId = UUID.randomUUID().toString();
        Boolean result;
        try {
            result = redisTemplate.opsForValue().setIfAbsent(resendKey,requestId,RESEND_COOLDOWN);
        } catch (DataAccessException e) {
            throw new EmailVerificationException(ErrorCode.EMAIL_VERIFICATION_UNAVAILABLE, e);
        }
        if (!Boolean.TRUE.equals(result)) {
            throw new EmailVerificationException(ErrorCode.EMAIL_RESEND_TOO_SOON);
        }
        String key = generateKey(email);
        String verifiedKey = VERIFIED_PREFIX + email;
        try {
            String code = generateCode();
            String hashedCode = hashCode(email, code);
            // 기존 번호와 인증 완료 상태는 SMTP 실패 시에도 사용할 수 있어야 한다.
            emailSender.send(email,"[BABI] 이메일 인증번호 안내",
                    "BABI 이메일 인증번호는 " + code + "입니다.\n인증번호는 5분 동안 유효합니다.");
            Long saveResult = redisTemplate.execute(
                    SAVE_CODE_SCRIPT,
                    List.of(key, verifiedKey, resendKey),
                    hashedCode,
                    String.valueOf(VERIFICATION_TTL.toMillis()),
                    requestId
            );
            if (!Long.valueOf(1L).equals(saveResult)) {
                throw new EmailVerificationException(ErrorCode.EMAIL_VERIFICATION_UNAVAILABLE);
            }
        } catch(RuntimeException e) {
            try {
                deleteIfMatches(resendKey, requestId);
            } catch (RuntimeException cleanupException) {
                e.addSuppressed(cleanupException);
            }
            if (e instanceof EmailVerificationException verificationException) {
                throw verificationException;
            }
            throw new EmailVerificationException(
                    e instanceof DataAccessException ? ErrorCode.EMAIL_VERIFICATION_UNAVAILABLE : ErrorCode.EMAIL_SEND_FAILED, e);
        }
    }

    private String hashCode(String email, String code) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(
                    verificationSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(keySpec);

            String data = email +":" + code;
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("이메일 인증번호 암호화 처리에 실패했습니다.",e);
        }
    }

    private void deleteIfMatches(String key, String expectedValue) {
        redisTemplate.execute(
                DELETE_IF_MATCHES_SCRIPT,
                List.of(key),
                expectedValue
        );
    }

    public EmailVerificationResponse verifyCode(String email, String code) {
        email = EmailNormalizer.normalize(email);
        String key = generateKey(email);
        String attemptsKey = ATTEMPTS_PREFIX + email;
        String verifiedKey = VERIFIED_PREFIX + email;
        String hashedCode = hashCode(email, code);
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String verificationToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        String tokenHash = hashVerificationToken(email, verificationToken);
        Long result;
        try {
            result = redisTemplate.execute(
                    VERIFY_CODE_SCRIPT,
                    List.of(key, attemptsKey, verifiedKey),
                    hashedCode,
                    String.valueOf(MAX_VERIFY_ATTEMPTS),
                    String.valueOf(VERIFIED_TTL.toMillis()),
                    String.valueOf(ATTEMPTS_TTL.toMillis()),
                    "proof:" + tokenHash
            );
        } catch (DataAccessException e) {
            throw new EmailVerificationException(ErrorCode.EMAIL_VERIFICATION_UNAVAILABLE, e);
        }
        if (Long.valueOf(-1L).equals(result)) {
            throw new EmailVerificationException(ErrorCode.EMAIL_CODE_EXPIRED);
        }
        if (Long.valueOf(-2L).equals(result)) {
            throw new EmailVerificationException(ErrorCode.EMAIL_ATTEMPTS_EXCEEDED);
        }
        if (Long.valueOf(0L).equals(result)) {
            throw new EmailVerificationException(ErrorCode.EMAIL_CODE_MISMATCH);
        }
        if (!Long.valueOf(1L).equals(result)) {
            throw new EmailVerificationException(ErrorCode.EMAIL_VERIFICATION_UNAVAILABLE);
        }
        return new EmailVerificationResponse(email, true, VERIFIED_TTL.toSeconds(), verificationToken);
    }

    private String hashVerificationToken(String email, String verificationToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = "BABI_EMAIL_VERIFICATION_V1\0" + email + "\0" + verificationToken;
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("이메일 인증 토큰 해시 처리에 실패했습니다.", e);
        }
    }

    public void reserveForSignup(String email, String verificationToken) {
        if (verificationToken == null || !verificationToken.matches("[A-Za-z0-9_-]{43}")) {
            throw new EmailVerificationException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("이메일 인증 예약에는 회원가입 트랜잭션이 필요합니다.");
        }
        String normalizedEmail = EmailNormalizer.normalize(email);
        String verifiedKey = VERIFIED_PREFIX + normalizedEmail;
        String tokenHash = hashVerificationToken(normalizedEmail, verificationToken);
        String reservation = "signup:" + tokenHash + ":" + UUID.randomUUID();
        Long result;
        try {
            result = redisTemplate.execute(RESERVE_SIGNUP_SCRIPT, List.of(verifiedKey), tokenHash, reservation);
        } catch (DataAccessException e) {
            throw new EmailVerificationException(ErrorCode.EMAIL_VERIFICATION_UNAVAILABLE, e);
        }
        if (Long.valueOf(-2L).equals(result)) {
            throw new EmailVerificationException(ErrorCode.EMAIL_VERIFICATION_IN_PROGRESS);
        }
        if (Long.valueOf(-1L).equals(result)) {
            throw new EmailVerificationException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        if (!Long.valueOf(1L).equals(result)) {
            throw new EmailVerificationException(ErrorCode.EMAIL_VERIFICATION_UNAVAILABLE);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                String action = status == STATUS_ROLLED_BACK ? "rollback" : "consume";
                try {
                    Long cleanupResult = redisTemplate.execute(
                            COMPLETE_SIGNUP_SCRIPT, List.of(verifiedKey), reservation, action, "proof:" + tokenHash);
                    if (!Long.valueOf(0L).equals(cleanupResult) && !Long.valueOf(1L).equals(cleanupResult)) {
                        log.warn("회원가입 이메일 인증 예약 정리 결과가 올바르지 않습니다. cleanupId={} action={} transactionStatus={} result={}",
                                UUID.randomUUID(), action, status, cleanupResult);
                    }
                } catch (RuntimeException e) {
                    log.warn("회원가입 이메일 인증 예약 정리에 실패했습니다. cleanupId={} action={} transactionStatus={}",
                            UUID.randomUUID(), action, status, sanitizedFailure(e));
                }
            }
        });
    }

    private Throwable sanitizedFailure(Throwable failure) {
        Throwable sanitized = new Throwable(failure.getClass().getName());
        sanitized.setStackTrace(failure.getStackTrace());
        Throwable target = sanitized;
        Throwable cause = failure.getCause();
        for (int depth = 0; cause != null && depth < 5; depth++) {
            Throwable safeCause = new Throwable(cause.getClass().getName());
            safeCause.setStackTrace(cause.getStackTrace());
            target.initCause(safeCause);
            target = safeCause;
            cause = cause.getCause();
        }
        return sanitized;
    }

}
