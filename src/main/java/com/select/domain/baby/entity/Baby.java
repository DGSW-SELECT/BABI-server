package com.select.domain.baby.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 음성 기록이 속한 아기.
 *
 * <p>현재 브랜치의 스키마는 가족 그룹 단위로 아기를 관리하므로
 * {@code family_group_id}를 함께 보관한다.</p>
 */
@Entity
@Getter
@Table(name = "babies")
public class Baby {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "family_group_id", nullable = false)
    private Long familyGroupId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Baby() {
    }

    @Builder
    public Baby(Long familyGroupId, String name, LocalDate birthDate, LocalDateTime createdAt) {
        this.familyGroupId = familyGroupId;
        this.name = name;
        this.birthDate = birthDate;
        this.createdAt = createdAt;
    }

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
