package com.select.domain.voice.repository;

import com.select.domain.voice.entity.SttStatus;
import com.select.domain.voice.entity.VoiceInput;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class VoiceInputRepositoryTest {

    @Autowired
    private VoiceInputRepository voiceInputRepository;

    @Test
    void savesConfirmedVoiceInputAndFindsItByBabyId() {
        VoiceInput saved = voiceInputRepository.save(VoiceInput.builder()
                .babyId(1L)
                .familyMemberId(7L)
                .transcript("분유 먹였어")
                .sttStatus(SttStatus.COMPLETED)
                .build());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(voiceInputRepository.findByIdAndBabyId(saved.getId(), 1L)).isPresent();
        assertThat(voiceInputRepository.findByIdAndBabyId(saved.getId(), 2L)).isEmpty();
    }

    @Test
    void updatesTranscriptAndUpdatedAt() {
        VoiceInput saved = voiceInputRepository.saveAndFlush(VoiceInput.builder()
                .babyId(1L)
                .familyMemberId(7L)
                .transcript("분유 먹였어")
                .sttStatus(SttStatus.COMPLETED)
                .build());
        var createdAt = saved.getCreatedAt();

        saved.updateTranscript("수정된 분유 기록");
        VoiceInput updated = voiceInputRepository.saveAndFlush(saved);

        assertThat(updated.getTranscript()).isEqualTo("수정된 분유 기록");
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(createdAt);
    }
}
