package com.select.domain.parenting.repository;

import com.select.domain.parenting.entity.ParentingRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParentingRecordRepository extends JpaRepository<ParentingRecord, Long> {

    List<ParentingRecord> findAllByBabyIdAndVoiceInputIdOrderByIdAsc(Long babyId, Long voiceInputId);
}
