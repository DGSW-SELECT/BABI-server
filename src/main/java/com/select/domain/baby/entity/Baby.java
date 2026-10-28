package com.select.domain.baby.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "babies")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Baby {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false,length = 50)
    private String name;

    @Column(nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false,name = "user_id")
    private Long createdByUserId;

    @Column(nullable = false, name = "created_at")
    private LocalDateTime createdAt;

    @Builder
    public Baby(String name, LocalDate birthDate, Long createdByUserId) {
        this.name = name;
        this.birthDate = birthDate;
        this.createdByUserId = createdByUserId;
    }

    @PrePersist
    public void setCreatedAt() {
        createdAt = LocalDateTime.now();
    }

    public void updateDetails(String name, LocalDate birthDate) {
        if (name != null) {
            this.name = name;
        }
        if (birthDate != null) {
            this.birthDate = birthDate;
        }
    }
}
