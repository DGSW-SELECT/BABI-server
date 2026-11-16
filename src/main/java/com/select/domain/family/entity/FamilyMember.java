package com.select.domain.family.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 가족 그룹에 속한 사용자.
 *
 * <p>음성 기록의 {@code family_member_id}는 이 구성원 ID를 사용한다.
 * JWT에는 사용자 ID만 있으므로, 요청자와 아기 소속을 확인할 때 이 엔티티를 조회한다.</p>
 */
@Entity
@Getter
@Table(
        name = "family_members",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_family_member",
                columnNames = {"family_group_id", "user_id"}
        )
)
public class FamilyMember {

    public static final String STATUS_ACTIVE = "ACTIVE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "family_group_id", nullable = false)
    private Long familyGroupId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 30)
    private String role;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    protected FamilyMember() {
    }

    @Builder
    public FamilyMember(
            Long familyGroupId,
            Long userId,
            String role,
            String status,
            LocalDateTime joinedAt
    ) {
        this.familyGroupId = familyGroupId;
        this.userId = userId;
        this.role = role;
        this.status = status;
        this.joinedAt = joinedAt;
    }

    @PrePersist
    public void onCreate() {
        if (joinedAt == null) {
            joinedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = STATUS_ACTIVE;
        }
    }
}
