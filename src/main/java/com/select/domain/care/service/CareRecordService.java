package com.select.domain.care.service;

import com.select.domain.baby.repository.BabyAccessRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.care.dto.request.CreateCareRecordRequest;
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
        requireMember(userId, babyId, "CARE_WRITE", true);
        return transactions.execute(status -> {
            babies.findByIdForUpdate(babyId);
            Long memberId = requireMember(userId, babyId, "CARE_WRITE", true);
            if (request.voiceInputId() != null && !records.ownsVoiceInput(babyId, memberId, request.voiceInputId())) {
                throw new CareRecordException(ErrorCode.INVALID_CARE_RECORD);
            }
            return records.save(babyId, memberId, request);
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

    private Long requireMember(Long userId, Long babyId, String permission, boolean writing) {
        return access.findMemberId(userId, babyId, permission, writing)
                .orElseThrow(() -> new AccessDeniedException("육아 기록 접근 권한이 없습니다."));
    }
}
