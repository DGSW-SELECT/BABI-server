package com.select.domain.baby.service;

import com.select.domain.auth.mail.EmailSender;
import com.select.domain.baby.entity.Baby;
import com.select.domain.baby.entity.BabyEmailInvitation;
import com.select.domain.baby.entity.BabyInvitationCode;
import com.select.domain.baby.entity.BabyMember;
import com.select.domain.baby.entity.BabyRole;
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
import java.util.function.Supplier;

@Service
public class BabyInvitationWorkflow {
    private final BabyRepository babies;
    private final BabyMemberRepository members;
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

    public BabyInvitationWorkflow(BabyRepository babies, BabyMemberRepository members,
            BabyInvitationCodeRepository codes, AllocatedBabyInvitationCodeRepository allocatedCodes, BabyEmailInvitationRepository invitations,
            UserRepository users, BabyInvitationService permissions, InvitationCodeGenerator generator,
            InvitationRateLimiter limiter, InvitationLinkFactory links, EmailSender sender,
            PlatformTransactionManager transactionManager) {
        this.babies = babies;
        this.members = members;
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
            return new BabyInvitationCodeResponse(requireUsableCode(getOrCreateCode(babyId)).value());
        }, !TransactionSynchronizationManager.isActualTransactionActive());
    }

    public BabyInvitationCodeResponse rotate(Long babyId, Long userId) {
        return withCodeRetry(() -> {
            lockOwnedBaby(babyId, userId);
            var current = codes.findByIdForUpdate(babyId).orElse(null);
            String value = generateUnusedCode(current == null ? null : current.value());
            if (current == null) {
                current = new BabyInvitationCode(babyId, value);
            } else {
                current.rotate(value);
            }
            codes.saveAndFlush(current);
            limiter.rotate(userId);
            invitations.findByBabyId(babyId).forEach(BabyEmailInvitation::revoke);
            return new BabyInvitationCodeResponse(current.value());
        });
    }

    public BabyInvitationResponse send(Long babyId, Long userId, String targetEmail) {
        String email = EmailNormalizer.normalize(targetEmail);
        return withCodeRetry(() -> {
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
            var code = requireUsableCode(getOrCreateCode(babyId));
            String link = links.create(code.value());
            limiter.send(userId, babyId, email);
            Instant now = Instant.now();
            var invitation = invitations.findForUpdate(babyId, email).orElse(null);
            if (invitation == null) {
                invitation = new BabyEmailInvitation(babyId, email, code.getGeneration(), now);
            } else if (!invitation.usable(code.getGeneration(), now)) {
                invitation.renew(code.getGeneration(), now);
            }
            invitations.saveAndFlush(invitation);
            try {
                sender.send(email, "[BABI] 아기 보호자 초대",
                        "BABI 아기 보호자로 초대되었습니다.\n초대 코드: " + code.value()
                                + "\n초대 링크: " + link + "\n이메일 초대 유효기간: " + invitation.getExpiresAt()
                                + "\n초대받은 이메일의 계정으로 로그인한 후 수락해 주세요.");
            } catch (RuntimeException e) {
                // Do not propagate SMTP exceptions containing recipient addresses or message bodies.
                throw new BabyInvitationException(ErrorCode.INVITATION_SEND_FAILED);
            }
            return response(baby, invitation);
        });
    }

    public BabyInvitationResponse inspect(String value, Long userId) {
        limiter.lookup(userId);
        Long babyId = resolveBaby(value);
        return transact(() -> {
            Baby baby = lockBaby(babyId);
            var invitation = eligibleInvitation(babyId, value, userId);
            return response(baby, invitation);
        });
    }

    public BabyInvitationResponse accept(String value, Long userId) {
        limiter.accept(userId);
        Long babyId = resolveBaby(value);
        return transact(() -> {
            Baby baby = lockBaby(babyId);
            var invitation = eligibleInvitation(babyId, value, userId);
            members.saveAndFlush(BabyMember.builder().babyId(babyId).userId(userId).role(BabyRole.MEMBER).build());
            invitation.accept();
            return response(baby, invitation);
        });
    }

    private BabyEmailInvitation eligibleInvitation(Long babyId, String value, Long userId) {
        var code = codes.findByIdForUpdate(babyId).orElseThrow(this::invalid);
        if (!code.value().equals(value) || !code.usable(Instant.now())) throw invalid();
        String email = EmailNormalizer.normalize(account(userId).getEmail());
        var invitation = invitations.findForUpdate(babyId, email).orElseThrow(this::invalid);
        if (!invitation.getEmail().equals(email)) throw invalid();
        if (!invitation.usable(code.getGeneration(), Instant.now())) throw invalid();
        if (members.existsByBabyIdAndUserId(babyId, userId)) {
            throw new BabyInvitationException(ErrorCode.INVITATION_ALREADY_MEMBER);
        }
        return invitation;
    }

    private Long resolveBaby(String value) {
        if (value == null || !value.matches("[A-Za-z0-9]{6}")) throw invalid();
        // Resolve only a scalar outside the mutation transaction; never cache an old code/owner snapshot.
        return codes.findBabyIdByCode(value.getBytes(StandardCharsets.US_ASCII)).orElseThrow(this::invalid);
    }

    private BabyInvitationCode getOrCreateCode(Long babyId) {
        return codes.findByIdForUpdate(babyId).orElseGet(() ->
                codes.saveAndFlush(new BabyInvitationCode(babyId, generateUnusedCode(null))));
    }

    private BabyInvitationCode requireUsableCode(BabyInvitationCode code) {
        if (!code.usable(Instant.now())) {
            throw new BabyInvitationException(ErrorCode.INVITATION_CODE_EXPIRED);
        }
        return code;
    }

    private String generateUnusedCode(String previous) {
        for (int attempt = 0; attempt < 20; attempt++) {
            String value = generator.generate();
            byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
            if (!value.equals(previous) && !codes.existsByCode(bytes) && !allocatedCodes.existsByCode(bytes)) {
                // A permanent UNIQUE reservation closes races with both allocations and retirements.
                allocatedCodes.saveAndFlush(new AllocatedBabyInvitationCode(value));
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

    private BabyInvitationException invalid() {
        // Same result for wrong account, expired, revoked, used, and unknown invitations.
        return new BabyInvitationException(ErrorCode.INVITATION_INVALID);
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
            } catch (DataIntegrityViolationException e) {
                boolean collision = false;
                for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                    if (cause instanceof ConstraintViolationException violation
                            && violation.getConstraintName() != null
                            && violation.getConstraintName().toLowerCase(java.util.Locale.ROOT)
                                    .contains("uk_baby_invitation_code")) collision = true;
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
