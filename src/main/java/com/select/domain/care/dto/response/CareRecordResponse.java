package com.select.domain.care.dto.response;

import com.select.domain.care.entity.CareRecordType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CareRecordResponse(
        Long recordId,
        Long babyId,
        Long authorMemberId,
        String authorNickname,
        Long voiceInputId,
        CareRecordType recordType,
        LocalDateTime occurredAt,
        String feedingType,
        Integer amountMl,
        LocalDateTime sleepEndAt,
        BigDecimal temperatureCelsius,
        String diaperType,
        String cryType,
        BigDecimal confidence,
        String note,
        LocalDateTime createdAt
) {
}
