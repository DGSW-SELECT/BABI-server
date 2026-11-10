package com.select.domain.growth.dto.response;

import java.math.BigDecimal;

public record GrowthComparisonResponse(GrowthRecordItem current, GrowthRecordItem previous, Change change) {
    public record Change(Long days, BigDecimal heightCm, BigDecimal weightKg) { }
}
