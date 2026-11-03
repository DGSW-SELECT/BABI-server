package com.select.domain.report.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record DailyReportResponse(
        Long reportId,
        Long babyId,
        LocalDate reportDate,
        String summary,
        String status,
        LocalDateTime generatedAt,
        SevenDayAverage sevenDayAverage
) {
    public record SevenDayAverage(
            Double averageDailyFeedingAmountMl,
            Double averageDailySleepMinutes,
            Double averageTemperatureCelsius
    ) {
    }
}