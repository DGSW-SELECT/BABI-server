package com.select.domain.baby.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "baby_email_invitations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BabyEmailInvitation {
    public enum Status { PENDING, ACCEPTED, REJECTED, CANCELED, EXPIRED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "baby_id", nullable = false) private Long babyId;
    @Column(name = "invited_by_member_id", nullable = false) private Long invitedByMemberId;
    @Column(name = "invited_email", nullable = false, length = 320) private String email;
    @Column(nullable = false, length = 30) private String role;
    @Column(name = "token_hash", nullable = false, length = 64, columnDefinition = "char(64)") private String tokenHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "accepted_user_id") private Long acceptedUserId;
    @Column(name = "responded_at") private Instant respondedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    public BabyEmailInvitation(Long babyId, String email, Long inviterId, String tokenHash, Instant now) {
        this.babyId = babyId; this.email = email; this.invitedByMemberId = inviterId;
        this.tokenHash = tokenHash; this.role = "GUARDIAN"; this.createdAt = now;
        this.expiresAt = now.plus(Duration.ofDays(14)); this.status = Status.PENDING;
    }
    public boolean usable(Instant now) { return status == Status.PENDING && expiresAt.isAfter(now); }
    public void revoke() { if (status == Status.PENDING) status = Status.CANCELED; }
    public void expire() { if (status == Status.PENDING) status = Status.EXPIRED; }
}
