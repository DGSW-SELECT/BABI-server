package com.select.domain.handover.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@Profile("!local")
public class UnavailableDailyCareRecordProvider implements DailyCareRecordProvider {

    @Override
    public List<String> findToday(Long babyId, LocalDate date) {
        return List.of();
    }
}
