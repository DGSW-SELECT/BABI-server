package com.select.domain.parenting.analyzer;

import java.time.LocalDateTime;
import java.util.List;

public interface ParentingRecordAnalyzer {

    List<ParentingRecordDraft> analyze(String transcript, LocalDateTime referenceTime);
}
