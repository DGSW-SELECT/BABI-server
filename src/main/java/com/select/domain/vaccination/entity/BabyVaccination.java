package com.select.domain.vaccination.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "baby_vaccinations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BabyVaccination {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long babyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vaccine_id", nullable = false)
    private Vaccine vaccine;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(name = "completed_date")
    private LocalDate completedDate;

    @Column(name = "completed")
    private Boolean completed;

    public void complete(LocalDate completedDate) {
        if (completedDate == null) {
            throw new IllegalArgumentException("완료날짜가 필요합니다.");
        }
        this.completedDate = completedDate;
        this.completed = true;
    }
    public void cancelComplete() {
        this.completedDate = null;
        this.completed = false;
    }
}
