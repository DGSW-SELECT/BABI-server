package com.select.domain.handover.service;

import com.select.domain.report.dto.response.DailyReportFacts;

import java.time.LocalDate;

public interface DailyCareRecordProvider {

    DailyReportFacts findToday(Long babyId, LocalDate date);
}
