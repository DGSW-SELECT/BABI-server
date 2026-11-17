package com.select.domain.handover.service;

import com.select.domain.report.dto.response.DailyReportFacts;
import com.select.domain.report.repository.DailyReportStatisticsRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@Profile("!handover-fake")
public class DatabaseDailyCareRecordProvider implements DailyCareRecordProvider {

    private final DailyReportStatisticsRepository statisticsRepository;

    public DatabaseDailyCareRecordProvider(DailyReportStatisticsRepository statisticsRepository) {
        this.statisticsRepository = statisticsRepository;
    }

    @Override
    public DailyReportFacts findToday(Long babyId, LocalDate date) {
        return statisticsRepository.findDailyFacts(babyId, date);
    }
}
