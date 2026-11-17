package com.select.domain.baby.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "baby_members",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_baby_member",
                columnNames = {"baby_id", "user_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BabyMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "baby_id",nullable = false)
    private Long babyId;

    @Column(name = "user_id",nullable = false)
    private Long userId;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private BabyRole role;

    @Column(name = "removed_at")
    private LocalDateTime removedAt;

    @Builder
    public BabyMember(Long babyId, Long userId, BabyRole role) {
        this.babyId = babyId;
        this.userId = userId;
        this.role = role;
    }

    public void changeRole(BabyRole role) {
        this.role = role;
    }

    public void deactivate() {
        this.removedAt = LocalDateTime.now();
    }

    public void reactivate(BabyRole role) {
        this.role = role;
        this.removedAt = null;
    }
}
