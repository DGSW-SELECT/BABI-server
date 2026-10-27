package com.select.domain.report.service;

import com.select.domain.report.dto.request.CreateDailyReportRequest;
import com.select.domain.report.dto.request.UpdateDailyReportRequest;
import com.select.domain.report.dto.response.DailyReportResponse;
import com.select.domain.report.entity.DailyReport;
import com.select.domain.report.exception.DailyReportAlreadyExistsException;
import com.select.domain.report.exception.DailyReportNotFoundException;
import com.select.domain.report.repository.BabyAccessRepository;
import com.select.domain.report.repository.DailyReportRepository;
import com.select.domain.report.repository.DailyReportStatisticsRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class DailyReportService {

    private static final String UNIQUE_CONSTRAINT = "uk_daily_reports_baby_date";

    private final DailyReportRepository dailyReportRepository;
    private final BabyAccessRepository babyAccessRepository;
    private final DailyReportStatisticsRepository statisticsRepository;

    public DailyReportService(
            DailyReportRepository dailyReportRepository,
            BabyAccessRepository babyAccessRepository,
            DailyReportStatisticsRepository statisticsRepository
    ) {
        this.dailyReportRepository = dailyReportRepository;
        this.babyAccessRepository = babyAccessRepository;
        this.statisticsRepository = statisticsRepository;
    }

    @Transactional
    public DailyReportResponse create(Long userId, Long babyId, CreateDailyReportRequest request) {
        verifyBabyAccess(userId, babyId);
        if (dailyReportRepository.existsByBabyIdAndReportDate(babyId, request.reportDate())) {
            throw new DailyReportAlreadyExistsException();
        }

        try {
            DailyReport report = dailyReportRepository.saveAndFlush(
                    DailyReport.create(babyId, request.reportDate(), request.summary())
            );
            return toResponse(report);
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateReportDate(e)) {
                throw new DailyReportAlreadyExistsException();
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public DailyReportResponse get(Long userId, Long babyId, java.time.LocalDate reportDate) {
        verifyBabyAccess(userId, babyId);
        return toResponse(findReport(babyId, reportDate));
    }

    @Transactional
    public DailyReportResponse update(
            Long userId,
            Long babyId,
            java.time.LocalDate reportDate,
            UpdateDailyReportRequest request
    ) {
        verifyBabyAccess(userId, babyId);
        DailyReport report = findReport(babyId, reportDate);
        report.updateSummary(request.summary());
        return toResponse(report);
    }

    @Transactional
    public void delete(Long userId, Long babyId, java.time.LocalDate reportDate) {
        verifyBabyAccess(userId, babyId);
        dailyReportRepository.delete(findReport(babyId, reportDate));
    }

    private void verifyBabyAccess(Long userId, Long babyId) {
        if (!babyAccessRepository.hasAccess(userId, babyId)) {
            throw new AccessDeniedException("아기에 대한 접근 권한이 없습니다.");
        }
    }

    private DailyReport findReport(Long babyId, java.time.LocalDate reportDate) {
        return dailyReportRepository.findByBabyIdAndReportDate(babyId, reportDate)
                .orElseThrow(DailyReportNotFoundException::new);
    }

    private DailyReportResponse toResponse(DailyReport report) {
        return new DailyReportResponse(
                report.getId(),
                report.getBabyId(),
                report.getReportDate(),
                report.getSummary(),
                report.getStatus(),
                report.getGeneratedAt(),
                statisticsRepository.findAverage(report.getBabyId(), report.getReportDate())
        );
    }

    private boolean isDuplicateReportDate(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation) {
                String constraintName = violation.getConstraintName();
                if (constraintName != null
                        && constraintName.toLowerCase(Locale.ROOT).contains(UNIQUE_CONSTRAINT)) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }
}
