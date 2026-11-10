package com.select.domain.report.repository;

import com.select.domain.report.entity.DailyReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyReportRepository extends JpaRepository<DailyReport, Long> {

    Optional<DailyReport> findByBabyIdAndReportDate(Long babyId, LocalDate reportDate);

    boolean existsByBabyIdAndReportDate(Long babyId, LocalDate reportDate);

    @Modifying
    @Query("delete from DailyReport report where report.id = :id and report.status = 'PENDING'")
    void deletePendingById(@Param("id") Long id);
}
