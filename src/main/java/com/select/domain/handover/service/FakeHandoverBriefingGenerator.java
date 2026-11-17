package com.select.domain.handover.service;

import com.select.domain.report.dto.response.DailyReportFacts;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

@Component
@Profile("handover-fake")
public class FakeHandoverBriefingGenerator implements HandoverBriefingGenerator {

    @Override
    public String generate(DailyReportFacts facts) {
        return "학습용 가짜 AI 브리핑입니다. 오늘 수유 " + facts.feedingCount() + "회를 정리했습니다.";
    }
}
