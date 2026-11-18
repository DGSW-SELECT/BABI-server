package com.select.domain.baby.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class BabyAccessRepository {

    private final JdbcTemplate jdbcTemplate;

    public BabyAccessRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean hasAccess(Long userId, Long babyId) {
        return findMemberId(userId, babyId, "REPORT_READ", false).isPresent();
    }

    public boolean hasWriteAccess(Long userId, Long babyId) {
        return findMemberId(userId, babyId, "REPORT_WRITE", true).isPresent();
    }

    public Optional<Long> findMemberId(Long userId, Long babyId, String permission, boolean writing) {
        return jdbcTemplate.query("""
                SELECT bm.id
                FROM active_baby_members bm
                JOIN users u ON u.id = bm.user_id
                WHERE bm.baby_id = ? AND bm.user_id = ? AND u.status = 'ACTIVE'
                  AND (? = FALSE OR bm.role <> 'VIEWER')
                  AND (bm.role = 'OWNER' OR EXISTS (
                      SELECT 1 FROM baby_permissions bp
                      WHERE bp.baby_id = bm.baby_id AND bp.baby_member_id = bm.id
                        AND bp.permission IN ('CARE_ALL', ?)
                  ))
                """, (rs, row) -> rs.getLong("id"), babyId, userId, writing, permission).stream().findFirst();
    }

    public List<BabyMemberAccess> findAccessibleMembers(Long userId, String permission, boolean writing) {
        return jdbcTemplate.query("""
                SELECT bm.baby_id, bm.id
                FROM active_baby_members bm
                JOIN users u ON u.id = bm.user_id
                WHERE bm.user_id = ? AND u.status = 'ACTIVE'
                  AND (? = FALSE OR bm.role <> 'VIEWER')
                  AND (bm.role = 'OWNER' OR EXISTS (
                      SELECT 1 FROM baby_permissions bp
                      WHERE bp.baby_id = bm.baby_id AND bp.baby_member_id = bm.id
                        AND bp.permission IN ('CARE_ALL', ?)
                  ))
                ORDER BY bm.baby_id
                """, (rs, row) -> new BabyMemberAccess(rs.getLong("baby_id"), rs.getLong("id")),
                userId, writing, permission);
    }

    public record BabyMemberAccess(Long babyId, Long memberId) {
    }
}
