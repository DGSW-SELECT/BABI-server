package com.select.domain.care.analyzer;

import com.select.domain.care.entity.DiaperType;
import com.select.domain.care.entity.FeedingType;
import com.select.domain.care.entity.CareRecordType;

import java.time.LocalDateTime;

public record CareRecordDraft(
        CareRecordType recordType,
        LocalDateTime occurredAt,
        Integer amountMl,
        Integer durationMinutes,
        FeedingType feedingType,
        DiaperType diaperType,
        String memo
) {
}
