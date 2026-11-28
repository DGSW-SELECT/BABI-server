package com.select.domain.care.service;

import com.select.domain.baby.repository.BabyAccessRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.care.dto.request.CreateCareRecordRequest;
import com.select.domain.care.dto.request.UpdateFeedingRequest;
import com.select.domain.care.dto.request.UpdateSleepRequest;
import com.select.domain.care.dto.request.UpdateTemperatureRequest;
import com.select.domain.care.dto.response.CareRecordPage;
import com.select.domain.care.dto.response.CareRecordResponse;
import com.select.domain.care.entity.CareRecordType;
import com.select.domain.care.exception.CareRecordException;
import com.select.domain.care.repository.CareRecordRepository;
import com.select.global.exception.ErrorCode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.BiPredicate;
import java.util.function.Function;

@Service
public class CareRecordService {
    private final BabyAccessRepository access;
    private final BabyRepository babies;
    private final CareRecordRepository records;
    private final TransactionTemplate transactions;

    public CareRecordService(BabyAccessRepository access, BabyRepository babies, CareRecordRepository records,
                             PlatformTransactionManager transactionManager) {
        this.access = access;
        this.babies = babies;
        this.records = records;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Transactional(propagation = Propagation.NEVER)
    public CareRecordResponse create(Long babyId, Long userId, CreateCareRecordRequest request) {
        if (!request.isValidForType()) throw new CareRecordException(ErrorCode.INVALID_CARE_RECORD);
        return withWriteAccess(babyId, userId, memberId -> {
            if (request.voiceInputId() != null && !records.ownsVoiceInput(babyId, memberId, request.voiceInputId())) {
                throw new CareRecordException(ErrorCode.INVALID_CARE_RECORD);
            }
            return records.save(babyId, memberId, request);
        });
    }

    @Transactional(propagation = Propagation.NEVER)
    public CareRecordResponse updateFeeding(Long babyId, Long userId, Long recordId, UpdateFeedingRequest request) {
        if (recordId <= 0 || isOutsideDatabaseYearRange(request.occurredAt())
                || (request.occurredAt() == null && request.feedingType() == null
                    && request.amountMl() == null && request.note() == null)) {
            throw new CareRecordException(ErrorCode.INVALID_CARE_RECORD);
        }
        return withWriteAccess(babyId, userId, memberId -> records.updateFeeding(babyId, recordId, request)
                .orElseThrow(() -> new CareRecordException(ErrorCode.FEEDING_RECORD_NOT_FOUND)));
    }

    @Transactional(propagation = Propagation.NEVER)
    public CareRecordResponse updateSleep(Long babyId, Long userId, Long recordId, UpdateSleepRequest request) {
        if (recordId <= 0 || isOutsideDatabaseYearRange(request.occurredAt())
                || isOutsideDatabaseYearRange(request.sleepEndAt())
                || (request.occurredAt() == null && request.sleepEndAt() == null && request.note() == null)) {
            throw new CareRecordException(ErrorCode.INVALID_CARE_RECORD);
        }
        return withWriteAccess(babyId, userId, memberId -> {
            var stored = records.findById(babyId, CareRecordType.SLEEP, recordId)
                    .orElseThrow(() -> new CareRecordException(ErrorCode.SLEEP_RECORD_NOT_FOUND));
            LocalDateTime startAt = request.occurredAt() == null ? stored.occurredAt() : request.occurredAt();
            LocalDateTime endAt = request.sleepEndAt() == null ? stored.sleepEndAt() : request.sleepEndAt();
            if (endAt != null && endAt.isBefore(startAt)) {
                throw new CareRecordException(ErrorCode.INVALID_CARE_RECORD);
            }
            return records.updateSleep(babyId, recordId, startAt, endAt, request.note())
                    .orElseThrow(() -> new CareRecordException(ErrorCode.SLEEP_RECORD_NOT_FOUND));
        });
    }

    @Transactional(propagation = Propagation.NEVER)
    public void deleteFeeding(Long babyId, Long userId, Long recordId) {
        deleteRecord(babyId, userId, recordId, records::deleteFeeding, ErrorCode.FEEDING_RECORD_NOT_FOUND);
    }

    @Transactional(propagation = Propagation.NEVER)
    public void deleteSleep(Long babyId, Long userId, Long recordId) {
        deleteRecord(babyId, userId, recordId, records::deleteSleep, ErrorCode.SLEEP_RECORD_NOT_FOUND);
    }

    private boolean isOutsideDatabaseYearRange(LocalDateTime dateTime) {
        return dateTime != null && (dateTime.getYear() < 1000 || dateTime.getYear() > 9999);
    }

    @Transactional(propagation = Propagation.NEVER)
    public CareRecordResponse updateTemperature(Long babyId, Long userId, Long recordId, UpdateTemperatureRequest request) {
        var occurredAt = request.occurredAt();
        if (recordId <= 0 || (occurredAt == null && request.temperatureCelsius() == null && request.note() == null)
                || (occurredAt != null && (occurredAt.getYear() < 1000 || occurredAt.getYear() > 9999))) {
            throw new CareRecordException(ErrorCode.INVALID_CARE_RECORD);
        }
        return withWriteAccess(babyId, userId, memberId -> records.updateTemperature(babyId, recordId, request)
                .orElseThrow(() -> new CareRecordException(ErrorCode.TEMPERATURE_RECORD_NOT_FOUND)));
    }

    @Transactional(propagation = Propagation.NEVER)
    public void deleteTemperature(Long babyId, Long userId, Long recordId) {
        deleteRecord(babyId, userId, recordId, records::deleteTemperature, ErrorCode.TEMPERATURE_RECORD_NOT_FOUND);
    }

    private void deleteRecord(Long babyId, Long userId, Long recordId,
                              BiPredicate<Long, Long> delete, ErrorCode notFound) {
        if (recordId <= 0) throw new CareRecordException(ErrorCode.INVALID_CARE_RECORD);
        withWriteAccess(babyId, userId, memberId -> {
            if (!delete.test(babyId, recordId)) {
                throw new CareRecordException(notFound);
            }
            return null;
        });
    }

    @Transactional(readOnly = true)
    public CareRecordPage list(Long babyId, Long userId, LocalDate date, CareRecordType type,
                               Long authorMemberId, int page, int size) {
        if (page < 0 || page > 10000 || size < 1 || size > 100 || date.getYear() < 1000
                || date.isAfter(LocalDate.of(9999, 12, 30)) || (authorMemberId != null && authorMemberId <= 0)) {
            throw new CareRecordException(ErrorCode.VALIDATION_ERROR);
        }
        requireMember(userId, babyId, "CARE_READ", false);
        var found = records.findDay(babyId, date, type, authorMemberId, size + 1, page * size);
        return new CareRecordPage(found.stream().limit(size).toList(), page, size, found.size() > size);
    }

    private <T> T withWriteAccess(Long babyId, Long userId, Function<Long, T> action) {
        requireMember(userId, babyId, "CARE_WRITE", true);
        return transactions.execute(status -> {
            babies.findByIdForUpdate(babyId);
            Long memberId = requireMember(userId, babyId, "CARE_WRITE", true);
            return action.apply(memberId);
        });
    }

    private Long requireMember(Long userId, Long babyId, String permission, boolean writing) {
        return access.findMemberId(userId, babyId, permission, writing)
                .orElseThrow(() -> new AccessDeniedException("육아 기록 접근 권한이 없습니다."));
    }
}
