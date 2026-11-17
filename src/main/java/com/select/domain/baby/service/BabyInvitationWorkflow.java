package com.select.domain.baby.service;

import com.select.domain.auth.mail.EmailSender;
import com.select.domain.baby.entity.Baby;
import com.select.domain.baby.entity.BabyEmailInvitation;
import com.select.domain.baby.entity.BabyInvitationCode;
import com.select.domain.baby.entity.BabyMember;
import com.select.domain.baby.entity.BabyRole;
import com.select.domain.baby.entity.BabyPermission;
import com.select.domain.baby.entity.AllocatedBabyInvitationCode;
import com.select.domain.baby.exception.BabyInvitationException;
import com.select.domain.baby.exception.BabyNotFoundException;
import com.select.domain.baby.repository.*;
import com.select.domain.baby.response.BabyInvitationCodeResponse;
import com.select.domain.baby.response.BabyInvitationResponse;
import com.select.domain.user.entity.User;
import com.select.domain.user.repository.UserRepository;
import com.select.global.exception.ErrorCode;
import com.select.global.util.EmailNormalizer;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.function.Supplier;

@Service
public class BabyInvitationWorkflow {
    private final BabyRepository babies;
    private final BabyMemberRepository members;
    private final BabyPermissionRepository memberPermissions;
    private final BabyInvitationCodeRepository codes;
    private final AllocatedBabyInvitationCodeRepository allocatedCodes;
    private final BabyEmailInvitationRepository invitations;
    private final UserRepository users;
    private final BabyInvitationService permissions;
    private final InvitationCodeGenerator generator;
    private final InvitationRateLimiter limiter;
    private final InvitationLinkFactory links;
    private final EmailSender sender;
    private final TransactionTemplate transactions;

    public BabyInvitationWorkflow(BabyRepository babies, BabyMemberRepository members, BabyPermissionRepository memberPermissions,
            BabyInvitationCodeRepository codes, AllocatedBabyInvitationCodeRepository allocatedCodes, BabyEmailInvitationRepository invitations,
            UserRepository users, BabyInvitationService permissions, InvitationCodeGenerator generator,
            InvitationRateLimiter limiter, InvitationLinkFactory links, EmailSender sender,
            PlatformTransactionManager transactionManager) {
        this.babies = babies;
        this.members = members;
        this.memberPermissions = memberPermissions;
        this.codes = codes;
        this.allocatedCodes = allocatedCodes;
        this.invitations = invitations;
        this.users = users;
        this.permissions = permissions;
        this.generator = generator;
        this.limiter = limiter;
        this.links = links;
        this.sender = sender;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public BabyInvitationCodeResponse getCode(Long babyId, Long userId) {
        return withCodeRetry(() -> {
            lockOwnedBaby(babyId, userId);
            return new BabyInvitationCodeResponse(requireUsableCode(getOrCreateCode(babyId, userId)).value());
        }, !TransactionSynchronizationManager.isActualTransactionActive());
    }

    public BabyInvitationCodeResponse rotate(Long babyId, Long userId) {
        return withCodeRetry(() -> {
            lockOwnedBaby(babyId, userId);
            var current = codes.findByIdForUpdate(babyId).orElse(null);
            String value = generateUnusedCode(current == null ? null : current.value());
            if (current != null) current.revoke();
            var replacement = codes.saveAndFlush(new BabyInvitationCode(babyId, value, ownerMemberId(babyId, userId)));
            limiter.rotate(userId);
            invitations.findByBabyId(babyId).forEach(BabyEmailInvitation::revoke);
            return new BabyInvitationCodeResponse(replacement.value());
        });
    }

    public BabyInvitationResponse send(Long babyId, Long userId, String targetEmail) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            // A caller-owned transaction could roll back after the external message was delivered.
            throw new BabyInvitationException(ErrorCode.INVITATION_UNAVAILABLE);
        }
        String email = EmailNormalizer.normalize(targetEmail);
        PreparedInvitation prepared = withCodeRetry(() -> {
            Baby baby = lockOwnedBaby(babyId, userId);
            User owner = account(userId);
            if (EmailNormalizer.normalize(owner.getEmail()).equals(email)) {
                throw new BabyInvitationException(ErrorCode.INVITATION_SELF);
            }
            users.findByEmail(email).filter(target -> EmailNormalizer.normalize(target.getEmail()).equals(email)).ifPresent(target -> {
                if (members.existsByBabyIdAndUserId(babyId, target.getId())) {
                    throw new BabyInvitationException(ErrorCode.INVITATION_ALREADY_MEMBER);
                }
            });
            var code = requireUsableCode(getOrCreateCode(babyId, userId));
            String link = links.create(code.value());
            limiter.send(userId, babyId, email);
            Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
            var invitation = invitations.findForUpdate(babyId, email).orElse(null);
            if (invitation != null && !invitation.getExpiresAt().isAfter(now)) {
                invitation.expire();
                invitations.saveAndFlush(invitation);
                invitation = null;
            }
            if (invitation == null) {
                invitation = new BabyEmailInvitation(babyId, email, ownerMemberId(babyId, userId), randomTokenHash(), now);
            }
            invitations.saveAndFlush(invitation);
            return new PreparedInvitation(invitation.getId(), code.value(), link, response(baby, invitation));
        });

        // The invitation and code have committed before the external call. Re-lock the baby
        // so rotation or ownership transfer cannot invalidate this message while it is sent.
        return transact(() -> {
            lockOwnedBaby(babyId, userId);
            var current = requireUsableCode(codes.findByIdForUpdate(babyId).orElseThrow(this::invalid));
            var invitation = invitations.findForUpdate(babyId, email).orElseThrow(this::invalid);
            if (!current.value().equals(prepared.code()) || !invitation.getId().equals(prepared.invitationId())
                    || !invitation.usable(Instant.now())) throw invalid();
            try {
                sender.send(email, "[BABI] 아기 보호자 초대",
                        "BABI 아기 보호자로 초대되었습니다.\n초대 코드: " + prepared.code()
                                + "\n초대 링크: " + prepared.link() + "\n이메일 초대 유효기간: " + prepared.response().expiresAt()
                                + "\n로그인한 후 초대 코드를 입력해 주세요.");
            } catch (RuntimeException e) {
                throw new BabyInvitationException(ErrorCode.INVITATION_SEND_FAILED);
            }
            return prepared.response();
        });
    }

    private record PreparedInvitation(Long invitationId, String code, String link, BabyInvitationResponse response) {}

    public BabyInvitationResponse inspect(String value, Long userId) {
        limiter.lookup(userId);
        Long babyId = resolveBaby(value);
        return transact(() -> {
            Baby baby = lockBaby(babyId);
            var code = eligibleCode(babyId, value, userId, Instant.now());
            return response(baby, code);
        });
    }

    public BabyInvitationResponse accept(String value, Long userId) {
        limiter.accept(userId);
        Long babyId = resolveBaby(value);
        return transact(() -> {
            if (users.findByIdForUpdate(userId).filter(User::isActive).isEmpty())
                throw new BabyInvitationException(ErrorCode.UNAUTHORIZED);
            Baby baby = lockBaby(babyId);
            Instant now = Instant.now();
            var code = eligibleCode(babyId, value, userId, now);
            code.consume(now);
            BabyMember member = members.findIncludingRemoved(babyId, userId).orElseGet(() ->
                    BabyMember.builder().babyId(babyId).userId(userId).role(BabyRole.GUARDIAN).build());
            member.reactivate(BabyRole.GUARDIAN);
            member = members.saveAndFlush(member);
            memberPermissions.save(new BabyPermission(babyId, member.getId(), BabyPermission.CARE_ALL));
            return response(baby, code);
        });
    }

    private BabyInvitationCode eligibleCode(Long babyId, String value, Long userId, Instant now) {
        var code = codes.findByIdForUpdate(babyId).orElseThrow(this::invalid);
        if (!code.value().equals(normalizeCode(value))) throw invalid();
        if (!account(userId).isActive()) throw new BabyInvitationException(ErrorCode.UNAUTHORIZED);
        if (members.existsByBabyIdAndUserId(babyId, userId)) {
            throw new BabyInvitationException(ErrorCode.INVITATION_ALREADY_MEMBER);
        }
        if (!code.getExpiresAt().isAfter(now)) throw invalid();
        if (code.getUsedCount() >= code.getMaxUses()) {
            throw new BabyInvitationException(ErrorCode.INVITATION_CODE_EXHAUSTED);
        }
        if (!code.usable(now)) throw invalid();
        return code;
    }

    private String normalizeCode(String value) {
        if (value == null || !value.matches("[A-Za-z0-9]{6}")) throw invalid();
        return value.toUpperCase(Locale.ROOT);
    }

    private Long resolveBaby(String value) {
        return codes.findBabyIdByCode(normalizeCode(value).getBytes(StandardCharsets.US_ASCII)).orElseThrow(this::invalid);
    }

    private BabyInvitationCode getOrCreateCode(Long babyId, Long userId) {
        return codes.findByIdForUpdate(babyId).orElseGet(() ->
                codes.saveAndFlush(new BabyInvitationCode(babyId, generateUnusedCode(null), ownerMemberId(babyId, userId))));
    }

    private Long ownerMemberId(Long babyId, Long userId) {
        return members.findByBabyIdAndUserId(babyId, userId).orElseThrow(this::invalid).getId();
    }

    private String randomTokenHash() {
        try {
            byte[] token = new byte[32];
            new java.security.SecureRandom().nextBytes(token);
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(token));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private BabyInvitationCode requireUsableCode(BabyInvitationCode code) {
        if (!code.getExpiresAt().isAfter(Instant.now())) {
            throw new BabyInvitationException(ErrorCode.INVITATION_CODE_EXPIRED);
        }
        if (!code.usable(Instant.now())) {
            throw new BabyInvitationException(ErrorCode.INVITATION_CODE_EXHAUSTED);
        }
        return code;
    }

    private String generateUnusedCode(String previous) {
        for (int attempt = 0; attempt < 20; attempt++) {
            String value = normalizeCode(generator.generate());
            byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
            if (!value.equals(previous) && !codes.existsByCode(bytes) && !allocatedCodes.existsByCode(bytes)) {
                // A permanent UNIQUE reservation closes races with both allocations and retirements.
                try {
                    allocatedCodes.saveAndFlush(new AllocatedBabyInvitationCode(value));
                } catch (DataIntegrityViolationException e) {
                    // This insert contains only the reserved code and its timestamp. Retry after rollback.
                    throw new AllocationCollision(e);
                }
                return value;
            }
        }
        throw new BabyInvitationException(ErrorCode.INVITATION_UNAVAILABLE);
    }

    private Baby lockOwnedBaby(Long babyId, Long userId) {
        Baby baby = lockBaby(babyId);
        permissions.validateOwner(babyId, userId);
        return baby;
    }

    private Baby lockBaby(Long babyId) {
        return babies.findByIdForUpdate(babyId).orElseThrow(BabyNotFoundException::new);
    }

    private User account(Long userId) {
        return users.findById(userId).orElseThrow(() -> new BabyInvitationException(ErrorCode.UNAUTHORIZED));
    }

    private BabyInvitationResponse response(Baby baby, BabyEmailInvitation invitation) {
        return new BabyInvitationResponse(baby.getId(), baby.getName(), invitation.getExpiresAt());
    }

    private BabyInvitationResponse response(Baby baby, BabyInvitationCode code) {
        return new BabyInvitationResponse(baby.getId(), baby.getName(), code.getExpiresAt());
    }

    private BabyInvitationException invalid() {
        // Same result for wrong account, expired, revoked, used, and unknown invitations.
        return new BabyInvitationException(ErrorCode.INVITATION_INVALID);
    }

    private static final class AllocationCollision extends RuntimeException {
        private AllocationCollision(Throwable cause) { super(cause); }
    }

    private <T> T transact(Supplier<T> action) {
        try {
            return transactions.execute(status -> action.get());
        } catch (DataAccessException e) {
            throw new BabyInvitationException(ErrorCode.INVITATION_UNAVAILABLE);
        }
    }

    private <T> T withCodeRetry(Supplier<T> action) {
        return withCodeRetry(action, false);
    }

    private <T> T withCodeRetry(Supplier<T> action, boolean retryLockFailure) {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                return transactions.execute(status -> action.get());
            } catch (AllocationCollision e) {
                // Retry the entire transaction after the failed reservation rolled back.
            } catch (DataIntegrityViolationException e) {
                boolean collision = false;
                for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                    if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                        String constraintName = violation.getConstraintName().toLowerCase(java.util.Locale.ROOT);
                        if (constraintName.contains("uk_baby_invitation_code")
                                || constraintName.contains("uk_invitation_code")) collision = true;
                    }
                }
                if (!collision) throw new BabyInvitationException(ErrorCode.INVITATION_UNAVAILABLE);
                // Unique-index race: retry after the failed transaction has fully rolled back, before any mail send.
            } catch (PessimisticLockingFailureException e) {
                // Code lookup/creation has no external side effects; retry only after rollback.
                // Send/rotate can already have invoked SMTP or Redis and must not be replayed.
                if (!retryLockFailure) throw new BabyInvitationException(ErrorCode.INVITATION_UNAVAILABLE);
            } catch (DataAccessException e) {
                throw new BabyInvitationException(ErrorCode.INVITATION_UNAVAILABLE);
            }
        }
        throw new BabyInvitationException(ErrorCode.INVITATION_UNAVAILABLE);
    }
}
