package com.select.domain.growth.entity;

import com.select.domain.baby.entity.Baby;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Entity
@Table
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

    @Column(name = "height_cd", precision = 5, scale = 2)
    private BigDecimal height;

    @Column(name = "weight_kg", precision = 5, scale = 2)
    private BigDecimal  weight;

    @Column(name = "measured_date", nullable = false)
    private Date measuredDate;

    @Column
    private String memo;

    @Column(name = "createdAt", insertable = false, updatable = false)
    private Date createdAt;

}
