package com.select.domain.voice.repository;

import com.select.domain.voice.entity.VoiceInput;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VoiceInputRepository extends JpaRepository<VoiceInput, Long> {

    Optional<VoiceInput> findByIdAndBabyId(Long id, Long babyId);
}
