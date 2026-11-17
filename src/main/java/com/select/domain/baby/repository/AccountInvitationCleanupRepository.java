package com.select.domain.baby.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Updates legacy invitation tables that do not all have JPA entities. */
@Repository
public class AccountInvitationCleanupRepository {
    private final JdbcTemplate jdbc;

    public AccountInvitationCleanupRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void cancelAndAnonymize(Long userId, String email) {
        jdbc.update("UPDATE family_invitations SET status = 'CANCELED' WHERE status = 'PENDING' AND (inviter_user_id = ? OR invited_user_id = ?)", userId, userId);
        jdbc.update("UPDATE baby_email_invitations SET status = 'CANCELED' WHERE status = 'PENDING' AND (LOWER(invited_email) = LOWER(?) OR invited_by_member_id IN (SELECT id FROM baby_members WHERE user_id = ?))", email, userId);
        jdbc.update("UPDATE baby_email_invitations SET invited_email = ? WHERE LOWER(invited_email) = LOWER(?)", "withdrawn-" + userId + "@deleted.invalid", email);
    }
}
