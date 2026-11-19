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
@Table(name = "handoff_briefings")
public class HandoverBriefing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "period_start")
    private LocalDateTime periodStart;

    @Column(name = "period_end")
    private LocalDateTime periodEnd;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "baby_id", nullable = false)
    private Long babyId;

    @Column(name = "briefing_date", nullable = false)
    private LocalDate briefingDate;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    protected HandoverBriefing() {
    }

    public HandoverBriefing(
            LocalDateTime periodStart,
            LocalDateTime periodEnd,
            String status,
            Long babyId,
            LocalDate briefingDate,
            String summary,
            LocalDateTime generatedAt
    ) {
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.status = status;
        this.babyId = babyId;
        this.briefingDate = briefingDate;
        this.summary = summary;
        this.generatedAt = generatedAt;
    }
}
