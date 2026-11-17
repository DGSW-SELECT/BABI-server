package com.select.domain.care.analyzer;

import java.time.LocalDateTime;
import java.util.List;

public interface CareRecordAnalyzer {

    List<CareRecordDraft> analyze(String transcript, LocalDateTime referenceTime);
}
