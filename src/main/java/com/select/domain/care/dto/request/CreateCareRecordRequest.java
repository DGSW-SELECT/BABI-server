package com.select.domain.care.dto.request;

import com.select.domain.care.entity.CareRecordType;
import com.select.domain.care.entity.DiaperType;
import com.select.domain.care.entity.FeedingType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateCareRecordRequest(
        @NotNull CareRecordType recordType,
        @NotNull LocalDateTime occurredAt,
        @Positive Long voiceInputId,
        FeedingType feedingType,
        @PositiveOrZero Integer amountMl,
        LocalDateTime sleepEndAt,
        @DecimalMin("30.0") @DecimalMax("45.0") @Digits(integer = 2, fraction = 1) BigDecimal temperatureCelsius,
        DiaperType diaperType,
        @Size(max = 50) String cryType,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal confidence,
        @Size(max = 5000) String note
) {
    @AssertTrue(message = "기록 유형에 맞는 값을 입력해 주세요.")
    public boolean isValidForType() {
        if (recordType == null || occurredAt == null) return false;
        if (occurredAt.getYear() < 1000 || occurredAt.getYear() > 9999
                || (sleepEndAt != null && (sleepEndAt.getYear() < 1000 || sleepEndAt.getYear() > 9999))) return false;
        return switch (recordType) {
            case FEEDING -> feedingType != null && sleepEndAt == null && temperatureCelsius == null
                    && diaperType == null && cryType == null && confidence == null;
            case SLEEP -> feedingType == null && amountMl == null && temperatureCelsius == null
                    && diaperType == null && cryType == null && confidence == null
                    && (sleepEndAt == null || !sleepEndAt.isBefore(occurredAt));
            case TEMPERATURE -> temperatureCelsius != null && feedingType == null && amountMl == null
                    && sleepEndAt == null && diaperType == null && cryType == null && confidence == null;
            case DIAPER -> diaperType != null && feedingType == null && amountMl == null
                    && sleepEndAt == null && temperatureCelsius == null && cryType == null && confidence == null;
            case CRY -> voiceInputId == null && feedingType == null && amountMl == null
                    && sleepEndAt == null && temperatureCelsius == null && diaperType == null;
        };
    }
}
