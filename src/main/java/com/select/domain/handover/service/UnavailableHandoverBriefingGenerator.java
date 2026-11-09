package com.select.domain.handover.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@Profile("!local")
public class UnavailableHandoverBriefingGenerator implements HandoverBriefingGenerator {

    @Override
    public String generate(Long babyId, LocalDate date, List<String> careRecords) {
        throw new UnsupportedOperationException("실제 AI 브리핑 생성기가 아직 연결되지 않았습니다.");
    }
}
