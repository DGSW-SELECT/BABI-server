package com.select.domain.handover.service;

import com.select.domain.report.dto.response.DailyReportFacts;

public interface HandoverBriefingGenerator {

    String generate(DailyReportFacts facts);
}
