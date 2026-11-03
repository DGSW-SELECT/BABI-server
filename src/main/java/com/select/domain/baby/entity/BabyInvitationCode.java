package com.select.domain.baby.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "baby_invitation_codes", uniqueConstraints =
        @UniqueConstraint(name = "uk_baby_invitation_code", columnNames = "code"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BabyInvitationCode {
    @Id
    private Long babyId;

    // Binary storage preserves case-sensitive uniqueness on MySQL as well as H2.
    @Column(nullable = false, length = 6)
    private byte[] code;

    @Column(nullable = false, length = 36)
    private String generation;

    @Column(nullable = false)
    private Instant expiresAt;

    public BabyInvitationCode(Long babyId, String code) {
        this.babyId = babyId;
        rotate(code);
    }

    public String value() {
        return new String(code, StandardCharsets.US_ASCII);
    }

    public void rotate(String code) {
        this.code = code.getBytes(StandardCharsets.US_ASCII);
        this.generation = UUID.randomUUID().toString();
        this.expiresAt = Instant.now().plus(Duration.ofDays(14));
    }

    public boolean usable(Instant now) {
        return expiresAt.isAfter(now);
    }
}
