package com.select.domain.handover.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "handover_briefings")
public class HandoverBriefing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long groupId;

    @Column(nullable = false)
    private Long babyId;

    @Column(nullable = false)
    private LocalDate briefingDate;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected HandoverBriefing() {
    }

    public HandoverBriefing(
            Long groupId,
            Long babyId,
            LocalDate briefingDate,
            String summary,
            LocalDateTime createdAt
    ) {
        this.groupId = groupId;
        this.babyId = babyId;
        this.briefingDate = briefingDate;
        this.summary = summary;
        this.createdAt = createdAt;
    }
}
