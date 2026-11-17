package com.select.domain.baby.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "baby_permissions")
@IdClass(BabyPermissionId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BabyPermission {
    public static final String CARE_ALL = "CARE_ALL";

    @Column(name = "baby_id", nullable = false)
    private Long babyId;

    @Id
    @Column(name = "baby_member_id", nullable = false)
    private Long babyMemberId;

    @Id
    @Column(nullable = false, length = 50)
    private String permission;

    public BabyPermission(Long babyId, Long babyMemberId, String permission) {
        this.babyId = babyId;
        this.babyMemberId = babyMemberId;
        this.permission = permission;
    }
}
