package com.select.domain.baby.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "baby_invitation_codes", uniqueConstraints = @UniqueConstraint(name = "uk_invitation_code", columnNames = "code"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BabyInvitationCode {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "baby_id", nullable = false) private Long babyId;
    @Column(nullable = false, length = 32) private String code;
    @Column(nullable = false, length = 30) private String role;
    @Column(name = "created_by_member_id", nullable = false) private Long createdByMemberId;
    @Column(name = "max_uses", nullable = false) private int maxUses;
    @Column(name = "used_count", nullable = false) private int usedCount;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    public BabyInvitationCode(Long babyId, String code, Long creatorId) {
        this.babyId = babyId;
        this.code = code;
        this.createdByMemberId = creatorId;
        this.role = "GUARDIAN";
        this.maxUses = 100; // Explicit application policy; existing rows retain their own limits.
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
        this.expiresAt = createdAt.plus(Duration.ofDays(14));
    }
    public String value() { return code; }
    public boolean usable(Instant now) { return "ACTIVE".equals(status) && expiresAt.isAfter(now) && usedCount < maxUses; }
    public void consume(Instant now) {
        if (!usable(now)) throw new IllegalStateException("Invitation code is unavailable");
        usedCount++;
    }
    public void revoke() { status = "REVOKED"; revokedAt = Instant.now(); }
}
