package com.select.domain.vaccination.entity;

import com.select.global.exception.BusinessException;
import com.select.global.exception.ErrorCode;
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

    @Column
    private String institution;

    @Column
    private String memo;

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
    public static BabyVaccination create(
            Long babyId,
            Vaccine vaccine,
            LocalDate scheduledDate,
            String institution,
            String memo
    ) {
        BabyVaccination vaccination = new BabyVaccination();
        vaccination.babyId = babyId;
        vaccination.vaccine = vaccine;
        vaccination.scheduledDate = scheduledDate;
        vaccination.institution = institution;
        vaccination.memo = memo;
        vaccination.completed = false;
        return vaccination;
    }
    public void updateSchedule(Vaccine vaccine, LocalDate scheduledDate, String institution, String memo) {
        if (Boolean.TRUE.equals(completed) {
            throw new BusinessException(ErrorCode.VACCINATION_ALREADY_COMPLETED);
        }
        
        this.vaccine = vaccine;
        this.scheduledDate = scheduledDate;
        this.institution = institution;
        this.memo = memo;
    }
}
