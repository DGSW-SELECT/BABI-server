package com.select.domain.growth.dto.response;

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
        LocalDateTime createdAt
) {
}
