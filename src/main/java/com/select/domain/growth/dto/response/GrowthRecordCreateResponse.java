package com.select.domain.growth.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;

public record GrowthRecordCreateResponse(
        Long growthRecordId,
        Long babyId,
        Long authorMemberId,
        BigDecimal height,
        BigDecimal weight,
        Date measuredDate,
        LocalDateTime createdAt
) {
}
