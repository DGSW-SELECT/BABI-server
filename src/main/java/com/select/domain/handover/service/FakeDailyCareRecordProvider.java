package com.select.domain.handover.service;

import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.time.LocalDate;
import java.util.List;

@Component
@Profile("local")
public class FakeDailyCareRecordProvider implements DailyCareRecordProvider {

    @Override
    public List<String> findToday(Long babyId, LocalDate date) {
        return List.of("학습용 수유 기록", "학습용 수면 기록", "학습용 체온 기록");
    }
}
