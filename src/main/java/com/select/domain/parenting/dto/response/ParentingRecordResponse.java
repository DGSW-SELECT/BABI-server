package com.select.domain.parenting.dto.response;

import com.select.domain.parenting.entity.DiaperType;
import com.select.domain.parenting.entity.FeedingType;
import com.select.domain.parenting.entity.ParentingRecord;
import com.select.domain.parenting.entity.ParentingRecordType;

import java.time.LocalDateTime;

public record ParentingRecordResponse(
        Long parentingRecordId,
        Long babyId,
        Long familyMemberId,
        Long voiceInputId,
        ParentingRecordType recordType,
        LocalDateTime occurredAt,
        Integer amountMl,
        Integer durationMinutes,
        FeedingType feedingType,
        DiaperType diaperType,
        String memo,
        LocalDateTime createdAt
) {

    public static ParentingRecordResponse from(ParentingRecord record) {
        return new ParentingRecordResponse(
                record.getId(),
                record.getBabyId(),
                record.getFamilyMemberId(),
                record.getVoiceInputId(),
                record.getRecordType(),
                record.getOccurredAt(),
                record.getAmountMl(),
                record.getDurationMinutes(),
                record.getFeedingType(),
                record.getDiaperType(),
                record.getMemo(),
                record.getCreatedAt()
        );
    }
}
