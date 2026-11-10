package com.select.domain.report.dto.response;

import java.time.LocalDate;

public record DailyReportFacts(
        LocalDate date,
        long feedingCount,
        long measuredFeedingCount,
        long totalFeedingMl,
        long completedSleepCount,
        long ongoingSleepCount,
        long completedSleepMinutes,
        long temperatureCount,
        Double averageTemperatureCelsius,
        Double minTemperatureCelsius,
        Double maxTemperatureCelsius,
        long diaperCount,
        long cryCount
) {
    public boolean hasRecords() {
        return feedingCount + completedSleepCount + ongoingSleepCount + temperatureCount + diaperCount + cryCount > 0;
    }
}
