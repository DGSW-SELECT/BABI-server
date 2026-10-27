package com.select.domain.handover.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class FakeHandoverBriefingGenerator implements HandoverBriefingGenerator {

    @Override
    public String generate(Long babyId, LocalDate date, List<String> careRecords) {
        return "학습용 가짜 AI 브리핑입니다. 오늘의 기록 " + careRecords.size() + "건을 정리했습니다.";
    }
}
