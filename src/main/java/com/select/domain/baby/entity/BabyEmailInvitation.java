package com.select.domain.baby.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "baby_email_invitations", uniqueConstraints =
        @UniqueConstraint(name = "uk_baby_invitation_email", columnNames = {"baby_id", "email"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BabyEmailInvitation {
    public enum Status { PENDING, ACCEPTED, REVOKED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "baby_id", nullable = false)
    private Long babyId;
    @Column(nullable = false, length = 320)
    private String email;
    @Column(nullable = false, length = 36)
    private String generation;
    @Column(nullable = false)
    private Instant sentAt;
    @Column(nullable = false)
    private Instant expiresAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    public BabyEmailInvitation(Long babyId, String email, String generation, Instant sentAt) {
        this.babyId = babyId;
        this.email = email;
        renew(generation, sentAt);
    }

    public boolean usable(String generation, Instant now) {
        return status == Status.PENDING && this.generation.equals(generation) && expiresAt.isAfter(now);
    }

    public void renew(String generation, Instant now) {
        this.generation = generation;
        this.sentAt = now;
        this.expiresAt = now.plus(Duration.ofDays(14));
        this.status = Status.PENDING;
    }

    public void revoke() {
        if (status == Status.PENDING) {
            status = Status.REVOKED;
        }
    }

    public void accept() {
        status = Status.ACCEPTED;
    }
}
