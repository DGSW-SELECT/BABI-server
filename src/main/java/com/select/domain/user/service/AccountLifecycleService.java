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
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbc;
    private final JwtProvider jwt;
    private final TokenRevocationService revocations;
    private final jakarta.persistence.EntityManager entityManager;

    public AccountLifecycleService(UserRepository users, BabyMemberRepository members, BabyPermissionRepository permissions,
            BabyRepository babies, PasswordEncoder passwords, JdbcTemplate jdbc, JwtProvider jwt, TokenRevocationService revocations,
            jakarta.persistence.EntityManager entityManager) {
        this.users = users; this.members = members; this.permissions = permissions; this.babies = babies;
        this.passwords = passwords; this.jdbc = jdbc; this.jwt = jwt; this.revocations = revocations; this.entityManager = entityManager;
    }

    public void logout(String token) {
        try { revocations.revoke(token, jwt.remainingLifetime(token)); }
        catch (org.springframework.dao.DataAccessException e) { throw new AccountLifecycleException(ErrorCode.AUTH_SERVICE_UNAVAILABLE); }
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
        jdbc.update("UPDATE family_invitations SET status = 'CANCELED' WHERE status = 'PENDING' AND (inviter_user_id = ? OR invited_user_id = ?)", userId, userId);
        jdbc.update("UPDATE baby_email_invitations SET status = 'CANCELED' WHERE status = 'PENDING' AND (LOWER(invited_email) = LOWER(?) OR invited_by_member_id IN (SELECT id FROM baby_members WHERE user_id = ?))", user.getEmail(), userId);
        jdbc.update("UPDATE baby_email_invitations SET invited_email = ? WHERE LOWER(invited_email) = LOWER(?)", "withdrawn-" + userId + "@deleted.invalid", user.getEmail());
        user.withdraw();
    }
}
