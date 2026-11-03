package com.select.domain.baby.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;

/** Append-only reservations prevent a retired code from ever identifying another invitation. */
@Entity
@Table(name = "allocated_baby_invitation_codes", uniqueConstraints =
        @UniqueConstraint(name = "uk_baby_invitation_code_allocation", columnNames = "code"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AllocatedBabyInvitationCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 6)
    private byte[] code;

    public AllocatedBabyInvitationCode(String code) {
        this.code = code.getBytes(StandardCharsets.US_ASCII);
    }
}
