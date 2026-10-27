package com.select.domain.handover.service;

import java.time.LocalDate;
import java.util.List;

public interface HandoverBriefingGenerator {

    String generate(Long babyId, LocalDate date, List<String> careRecords);
}
