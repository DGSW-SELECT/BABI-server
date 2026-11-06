package com.select.domain.baby.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Permanent code reservation; rows must not be removed when an invitation is revoked. */
@Entity
@Table(name = "allocated_baby_invitation_codes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AllocatedBabyInvitationCode {
    @Id
    @Column(nullable = false, length = 32)
    private String code;

    @Column(name = "allocated_at", nullable = false)
    private Instant allocatedAt;

    public AllocatedBabyInvitationCode(String code) {
        this.code = code;
        this.allocatedAt = Instant.now();
    }
}
