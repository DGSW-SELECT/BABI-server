package com.select.domain.user.service;

import com.select.domain.baby.entity.BabyRole;
import com.select.domain.baby.repository.BabyMemberRepository;
import com.select.domain.baby.repository.BabyPermissionRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.user.exception.InvalidCredentialsException;
import com.select.domain.user.repository.UserRepository;
import com.select.global.exception.ErrorCode;
import com.select.domain.user.exception.AccountLifecycleException;
import com.select.global.security.jwt.JwtProvider;
import com.select.global.security.jwt.TokenRevocationService;
import com.select.domain.baby.repository.AccountInvitationCleanupRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountLifecycleService {
    private final UserRepository users;
    private final BabyMemberRepository members;
    private final BabyPermissionRepository permissions;
    private final BabyRepository babies;
    private final PasswordEncoder passwords;
    private final AccountInvitationCleanupRepository invitations;
    private final JwtProvider jwt;
    private final com.select.global.security.jwt.RefreshSessionService sessions;
    private final TokenRevocationService revocations;
    private final jakarta.persistence.EntityManager entityManager;

    public AccountLifecycleService(UserRepository users, BabyMemberRepository members, BabyPermissionRepository permissions,
            BabyRepository babies, PasswordEncoder passwords, AccountInvitationCleanupRepository invitations, JwtProvider jwt, TokenRevocationService revocations,
            jakarta.persistence.EntityManager entityManager, com.select.global.security.jwt.RefreshSessionService sessions) {
        this.users = users; this.members = members; this.permissions = permissions; this.babies = babies;
        this.passwords = passwords; this.invitations = invitations; this.jwt = jwt; this.revocations = revocations; this.entityManager = entityManager; this.sessions = sessions;
    }

    public void logout(String accessToken, String refreshToken) {
        if (accessToken != null) {
            try {
                jwt.getUserId(accessToken);
                revocations.revoke(accessToken, jwt.remainingLifetime(accessToken));
                sessions.revokeSession(jwt.getSessionId(accessToken));
            } catch (io.jsonwebtoken.JwtException | IllegalArgumentException ignored) {
                // Expired or invalid access tokens must not prevent refresh-token logout.
            } catch (org.springframework.dao.DataAccessException e) {
                throw new AccountLifecycleException(ErrorCode.AUTH_SERVICE_UNAVAILABLE);
            }
        }
        sessions.revoke(refreshToken);
    }

    @Transactional
    public void withdraw(Long userId, String password) {
        var user = users.findByIdForUpdate(userId).filter(u -> u.isActive()).orElseThrow(InvalidCredentialsException::new);
        if (!passwords.matches(password, user.getPasswordHash())) throw new InvalidCredentialsException();
        var activeMembers = members.findByUserId(userId);
        activeMembers.stream().map(m -> m.getBabyId()).distinct().sorted().forEach(id -> babies.findByIdForUpdate(id).orElseThrow());
        for (var member : activeMembers) {
            entityManager.refresh(member);
            if (member.getRemovedAt() != null) continue;
            var otherMembers = members.findByBabyId(member.getBabyId()).stream().filter(m -> !m.getId().equals(member.getId())).toList();
            if (member.getRole() == BabyRole.OWNER && !otherMembers.isEmpty()
                    && otherMembers.stream().noneMatch(m -> m.getRole() == BabyRole.OWNER)) {
                throw new AccountLifecycleException(ErrorCode.ACCOUNT_OWNER_TRANSFER_REQUIRED);
            }
        }
        for (var member : activeMembers) {
            if (member.getRemovedAt() != null) continue;
            permissions.deleteByBabyIdAndBabyMemberId(member.getBabyId(), member.getId());
            member.deactivate();
        }
        invitations.cancelAndAnonymize(userId, user.getEmail());
        user.withdraw();
    }
}
