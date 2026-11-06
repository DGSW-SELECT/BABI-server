package com.select.domain.report.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BabyAccessRepository {

    private final JdbcTemplate jdbcTemplate;

    public BabyAccessRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean hasAccess(Long userId, Long babyId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM active_baby_members bm
                    JOIN users u ON u.id = bm.user_id
                    LEFT JOIN baby_permissions bp
                      ON bp.baby_id = bm.baby_id AND bp.baby_member_id = bm.id
                    WHERE bm.baby_id = ? AND bm.user_id = ?
                      AND u.status = 'ACTIVE'
                      AND (bm.role = 'OWNER' OR bp.baby_member_id IS NOT NULL)
                )
                """, Boolean.class, babyId, userId));
    }
}
