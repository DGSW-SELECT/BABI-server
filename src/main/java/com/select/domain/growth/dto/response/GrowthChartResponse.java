package com.select.domain.growth.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record GrowthChartResponse(LocalDate birthDate, List<Measurement> measurements,
        String standardStatus, String source, String sourceUrl, List<ReferencePoint> reference) {
    public record Measurement(Long growthRecordId, LocalDate measuredDate, long ageDays,
            BigDecimal heightCm, BigDecimal weightKg) {}
    public record ReferencePoint(int ageMonths, LocalDate date, Band heightCm, Band weightKg) {}
    public record Band(BigDecimal p3, BigDecimal p50, BigDecimal p97) {}
}
