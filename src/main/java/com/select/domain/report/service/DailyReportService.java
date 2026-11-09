package com.select.domain.report.service;

import com.select.domain.report.dto.request.CreateDailyReportRequest;
import com.select.domain.report.dto.request.UpdateDailyReportRequest;
import com.select.domain.report.dto.response.DailyReportResponse;
import com.select.domain.report.dto.response.DailyReportFacts;
import com.select.domain.report.entity.DailyReport;
import com.select.domain.report.exception.DailyReportAlreadyExistsException;
import com.select.domain.report.exception.DailyReportNotFoundException;
import com.select.domain.baby.repository.BabyAccessRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.report.exception.DailyReportGenerationException;
import com.select.global.exception.ErrorCode;
import com.select.domain.report.repository.DailyReportRepository;
import com.select.domain.report.repository.DailyReportStatisticsRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.function.Supplier;

@Service
public class DailyReportService {

    private static final String UNIQUE_CONSTRAINT = "uk_daily_report";

    private final DailyReportRepository dailyReportRepository;
    private final BabyAccessRepository babyAccessRepository;
    private final DailyReportStatisticsRepository statisticsRepository;
    private final BabyRepository babyRepository;
    private final OpenAiDailyReportGenerator generator;
    private final TransactionTemplate transactions;

    public DailyReportService(
            DailyReportRepository dailyReportRepository,
            BabyAccessRepository babyAccessRepository,
            DailyReportStatisticsRepository statisticsRepository,
            BabyRepository babyRepository,
            OpenAiDailyReportGenerator generator,
            PlatformTransactionManager transactionManager
    ) {
        this.dailyReportRepository = dailyReportRepository;
        this.babyAccessRepository = babyAccessRepository;
        this.statisticsRepository = statisticsRepository;
        this.babyRepository = babyRepository;
        this.generator = generator;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Transactional(propagation = Propagation.NEVER)
    public DailyReportResponse generate(Long userId, Long babyId, java.time.LocalDate date) {
        if (date.getYear() < 1000 || date.isAfter(java.time.LocalDate.of(9999, 12, 30))) {
            throw new DailyReportGenerationException(ErrorCode.VALIDATION_ERROR);
        }
        Generation generation = withWriteAccess(userId, babyId, () -> reserveGeneration(babyId, date));
        try {
            String summary = generator.generate(generation.facts());
            return withWriteAccess(userId, babyId, () -> {
                DailyReport report = dailyReportRepository.findByBabyIdAndReportDate(babyId, date)
                        .filter(current -> current.getId().equals(generation.reportId()) && current.isPending())
                        .orElseThrow(() -> new DailyReportGenerationException(ErrorCode.DAILY_REPORT_GENERATION_UNAVAILABLE));
                report.completeGeneration(summary);
                return toResponse(report);
            });
        } catch (RuntimeException | Error failure) {
            try {
                transactions.executeWithoutResult(status -> dailyReportRepository.deletePendingById(generation.reportId()));
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private Generation reserveGeneration(Long babyId, java.time.LocalDate date) {
        dailyReportRepository.findByBabyIdAndReportDate(babyId, date).ifPresent(existing -> {
            // Recover abandoned claims after five minutes; a new row ID fences out the old caller.
            if (!existing.isPending() || !existing.getGeneratedAt().isBefore(LocalDateTime.now().minusMinutes(5))) {
                throw new DailyReportAlreadyExistsException();
            }
            dailyReportRepository.delete(existing);
            dailyReportRepository.flush();
        });
        var facts = statisticsRepository.findDailyFacts(babyId, date);
        if (!facts.hasRecords()) throw new DailyReportGenerationException(ErrorCode.DAILY_REPORT_NO_RECORDS);
        var report = dailyReportRepository.saveAndFlush(DailyReport.pending(babyId, date));
        return new Generation(report.getId(), facts);
    }

    private record Generation(Long reportId, DailyReportFacts facts) { }

    @Transactional(propagation = Propagation.NEVER)
    public DailyReportResponse create(Long userId, Long babyId, CreateDailyReportRequest request) {
        return withWriteAccess(userId, babyId, () -> {
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
        });
    }

    @Transactional(readOnly = true)
    public DailyReportResponse get(Long userId, Long babyId, java.time.LocalDate reportDate) {
        verifyBabyAccess(userId, babyId);
        return toResponse(findReport(babyId, reportDate));
    }

    @Transactional(propagation = Propagation.NEVER)
    public DailyReportResponse update(
            Long userId,
            Long babyId,
            java.time.LocalDate reportDate,
            UpdateDailyReportRequest request
    ) {
        return withWriteAccess(userId, babyId, () -> {
            DailyReport report = findReport(babyId, reportDate);
            report.updateSummary(request.summary());
            return toResponse(report);
        });
    }

    @Transactional(propagation = Propagation.NEVER)
    public void delete(Long userId, Long babyId, java.time.LocalDate reportDate) {
        withWriteAccess(userId, babyId, () -> {
            dailyReportRepository.delete(findReport(babyId, reportDate));
            return null;
        });
    }

    private void verifyBabyAccess(Long userId, Long babyId) {
        if (!babyAccessRepository.hasAccess(userId, babyId)) {
            throw new AccessDeniedException("아기에 대한 접근 권한이 없습니다.");
        }
    }

    private <T> T withWriteAccess(Long userId, Long babyId, Supplier<T> action) {
        // Keep the precheck outside MySQL's transaction snapshot so later revocations remain visible.
        requireWritePermission(userId, babyId);
        return transactions.execute(status -> {
            babyRepository.findByIdForUpdate(babyId);
            requireWritePermission(userId, babyId);
            return action.get();
        });
    }

    private void requireWritePermission(Long userId, Long babyId) {
        if (!babyAccessRepository.hasWriteAccess(userId, babyId)) {
            throw new AccessDeniedException("리포트 작성 권한이 없습니다.");
        }
    }

    private DailyReport findReport(Long babyId, java.time.LocalDate reportDate) {
        return dailyReportRepository.findByBabyIdAndReportDate(babyId, reportDate)
                .filter(report -> !report.isPending())
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
