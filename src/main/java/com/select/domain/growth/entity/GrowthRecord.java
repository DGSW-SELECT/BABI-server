package com.select.domain.growth.entity;

import com.select.domain.baby.entity.Baby;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

@Entity
@Table(name = "growth_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GrowthRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "baby_id",nullable = false)
    private Baby baby;

    @Column(name = "author_member_id")
    private Long authorMemberId;

    @Column(name = "height_cm", precision = 5, scale = 2)
    private BigDecimal height;

    @Column(name = "weight_kg", precision = 5, scale = 2)
    private BigDecimal  weight;

    @Column(name = "measured_date", nullable = false)
    private LocalDate measuredDate;

    @Column
    private String memo;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public GrowthRecord(
            Baby baby,
            Long authorMemberId,
            BigDecimal heightCm,
            BigDecimal weightKg,
            LocalDate measuredDate,
            String memo
    ) {
        this.baby = baby;
        this.authorMemberId = authorMemberId;
        this.height = heightCm;
        this.weight = weightKg;
        this.measuredDate = measuredDate;
        this.memo = memo;
    }
}
