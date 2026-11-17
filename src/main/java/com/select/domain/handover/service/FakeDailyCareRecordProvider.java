package com.select.domain.handover.service;

import com.select.domain.report.dto.response.DailyReportFacts;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.time.LocalDate;

@Component
@Profile("handover-fake")
public class FakeDailyCareRecordProvider implements DailyCareRecordProvider {

    @Override
    public DailyReportFacts findToday(Long babyId, LocalDate date) {
        return new DailyReportFacts(date, 2, 2, 240, 1, 0, 60,
                1, 36.7, 36.7, 36.7, 1, 0);
    }
}
