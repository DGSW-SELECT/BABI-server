package com.select.domain.parenting.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "parenting_records",
        indexes = {
                @Index(name = "idx_parenting_records_baby_id", columnList = "baby_id"),
                @Index(name = "idx_parenting_records_voice_input_id", columnList = "voice_input_id")
        }
)
public class ParentingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "baby_id", nullable = false)
    private Long babyId;

    @Column(name = "family_member_id", nullable = false)
    private Long familyMemberId;

    @Column(name = "voice_input_id", nullable = false)
    private Long voiceInputId;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type", nullable = false, length = 20)
    private ParentingRecordType recordType;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "amount_ml")
    private Integer amountMl;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "feeding_type", length = 20)
    private FeedingType feedingType;

    @Enumerated(EnumType.STRING)
    @Column(name = "diaper_type", length = 20)
    private DiaperType diaperType;

    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ParentingRecord() {
    }

    @Builder
    public ParentingRecord(
            Long babyId,
            Long familyMemberId,
            Long voiceInputId,
            ParentingRecordType recordType,
            LocalDateTime occurredAt,
            Integer amountMl,
            Integer durationMinutes,
            FeedingType feedingType,
            DiaperType diaperType,
            String memo
    ) {
        this.babyId = babyId;
        this.familyMemberId = familyMemberId;
        this.voiceInputId = voiceInputId;
        this.recordType = recordType;
        this.occurredAt = occurredAt;
        this.amountMl = amountMl;
        this.durationMinutes = durationMinutes;
        this.feedingType = feedingType;
        this.diaperType = diaperType;
        this.memo = memo;
    }

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
