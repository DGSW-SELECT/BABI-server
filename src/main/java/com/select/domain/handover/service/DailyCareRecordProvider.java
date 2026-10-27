package com.select.domain.handover.service;

import java.time.LocalDate;
import java.util.List;

public interface DailyCareRecordProvider {

    List<String> findToday(Long babyId, LocalDate date);
}
