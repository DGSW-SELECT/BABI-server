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
                    FROM babies b
                    JOIN family_groups fg ON fg.id = b.family_group_id
                    WHERE b.id = ? AND fg.owner_user_id = ?
                ) OR EXISTS (
                    SELECT 1
                    FROM baby_permissions bp
                    JOIN family_members fm ON fm.id = bp.family_member_id
                    WHERE bp.baby_id = ? AND fm.user_id = ?
                )
                """, Boolean.class, babyId, userId, babyId, userId));
    }
}
