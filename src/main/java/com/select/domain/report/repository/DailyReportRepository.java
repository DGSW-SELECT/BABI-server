package com.select.domain.report.repository;

import com.select.domain.report.entity.DailyReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyReportRepository extends JpaRepository<DailyReport, Long> {

    Optional<DailyReport> findByBabyIdAndReportDate(Long babyId, LocalDate reportDate);

    boolean existsByBabyIdAndReportDate(Long babyId, LocalDate reportDate);
}