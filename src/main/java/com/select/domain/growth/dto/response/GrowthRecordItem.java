package com.select.domain.growth.dto.response;

import com.select.domain.growth.entity.GrowthRecord;
import java.math.BigDecimal;
import java.time.LocalDate;

public record GrowthRecordItem(Long growthRecordId, LocalDate measuredDate, BigDecimal heightCm,
                               BigDecimal weightKg, String memo) {
    public static GrowthRecordItem from(GrowthRecord record) {
        return new GrowthRecordItem(record.getId(), record.getMeasuredDate(), record.getHeight(), record.getWeight(), record.getMemo());
    }
}
