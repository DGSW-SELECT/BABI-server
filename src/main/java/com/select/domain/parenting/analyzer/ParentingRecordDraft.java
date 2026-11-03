package com.select.domain.parenting.analyzer;

import com.select.domain.parenting.entity.DiaperType;
import com.select.domain.parenting.entity.FeedingType;
import com.select.domain.parenting.entity.ParentingRecordType;

import java.time.LocalDateTime;

public record ParentingRecordDraft(
        ParentingRecordType recordType,
        LocalDateTime occurredAt,
        Integer amountMl,
        Integer durationMinutes,
        FeedingType feedingType,
        DiaperType diaperType,
        String memo
) {
}
