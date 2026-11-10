package com.select.domain.growth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record GrowthRecordCreateResponse(
        Long growthRecordId,
        Long babyId,
        BigDecimal height,
        BigDecimal weight,
        LocalDate measuredDate,
        String memo,
        LocalDateTime createdAt,
        Long authorMemberId
) {
    public GrowthRecordCreateResponse(Long growthRecordId, Long babyId, BigDecimal height, BigDecimal weight,
                                      LocalDate measuredDate, String memo, LocalDateTime createdAt) {
        this(growthRecordId, babyId, height, weight, measuredDate, memo, createdAt, null);
    }
    @JsonProperty("heightCm") public BigDecimal heightCm() { return height; }
    @JsonProperty("weightKg") public BigDecimal weightKg() { return weight; }
}
