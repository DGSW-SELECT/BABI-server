package com.select.domain.report.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "daily_reports",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_daily_reports_baby_date",
                columnNames = {"baby_id", "report_date"}
        )
)
public class DailyReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "baby_id", nullable = false)
    private Long babyId;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    protected DailyReport() {
    }

    public static DailyReport create(Long babyId, LocalDate reportDate, String summary) {
        DailyReport report = new DailyReport();
        report.babyId = babyId;
        report.reportDate = reportDate;
        report.summary = summary;
        report.status = "COMPLETED";
        report.generatedAt = LocalDateTime.now();
        return report;
    }

    public void updateSummary(String summary) {
        this.summary = summary;
    }
}