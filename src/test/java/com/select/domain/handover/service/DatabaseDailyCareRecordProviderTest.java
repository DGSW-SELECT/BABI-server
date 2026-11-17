package com.select.domain.handover.service;

import com.select.domain.report.dto.response.DailyReportFacts;
import com.select.domain.report.repository.DailyReportStatisticsRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseDailyCareRecordProviderTest {

    @Test
    void loadsSelectedBabysFactsForRequestedDate() {
        DailyReportStatisticsRepository repository = mock(DailyReportStatisticsRepository.class);
        DatabaseDailyCareRecordProvider provider = new DatabaseDailyCareRecordProvider(repository);
        LocalDate date = LocalDate.of(2026, 11, 17);
        DailyReportFacts facts = new DailyReportFacts(date, 1, 1, 120,
                0, 0, 0, 0, null, null, null, 0, 0);
        when(repository.findDailyFacts(2L, date)).thenReturn(facts);

        assertSame(facts, provider.findToday(2L, date));
        verify(repository).findDailyFacts(2L, date);
    }
}
