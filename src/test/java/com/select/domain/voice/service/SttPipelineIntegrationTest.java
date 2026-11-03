package com.select.domain.voice.service;

import com.select.domain.parenting.entity.ParentingRecord;
import com.select.domain.parenting.entity.ParentingRecordType;
import com.select.domain.parenting.repository.ParentingRecordRepository;
import com.select.domain.voice.client.SttClient;
import com.select.domain.voice.client.dto.response.SttResponse;
import com.select.domain.voice.dto.response.SttJobResponse;
import com.select.domain.voice.entity.SttJob;
import com.select.domain.voice.entity.SttStatus;
import com.select.domain.voice.entity.VoiceInput;
import com.select.domain.voice.repository.SttJobRepository;
import com.select.domain.voice.repository.VoiceInputRepository;
import com.select.domain.voice.storage.AudioStorage;
import com.select.global.config.SttJobProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 실제 DB·파일 저장소를 사용해 제출 -> 선점 -> STT -> 저장 흐름을 검증한다.
 */
@SpringBootTest
class SttPipelineIntegrationTest {

    @Autowired
    private SttJobService sttJobService;

    @Autowired
    private SttJobRepository sttJobRepository;

    @Autowired
    private VoiceInputRepository voiceInputRepository;

    @Autowired
    private ParentingRecordRepository parentingRecordRepository;

    @Autowired
    private AudioStorage audioStorage;

    @Autowired
    private SttJobProperties sttJobProperties;

    @MockitoBean
    private SttClient sttClient;

    private SttJobWorker sttJobWorker;

    @BeforeEach
    void setUp() {
        sttJobWorker = new SttJobWorker(sttJobService, sttClient, audioStorage, sttJobProperties);
    }

    @Test
    void submittedJobIsTranscribedAndSavedAsVoiceInputAndParentingRecord() {
        when(sttClient.transcribe(any(MultipartFile.class)))
                .thenReturn(new SttResponse("분유 120ml 먹였어요"));
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "voice.mp3", "audio/mpeg", "audio".getBytes(StandardCharsets.UTF_8)
        );

        SttJobResponse submitted = sttJobService.submit(1L, 7L, audio);

        assertThat(submitted.status()).isEqualTo(SttStatus.PENDING);
        assertThat(sttJobService.claim(submitted.jobId())).isTrue();

        sttJobWorker.process(submitted.jobId());

        SttJob completed = sttJobRepository.findById(submitted.jobId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(SttStatus.COMPLETED);
        assertThat(completed.getTranscript()).isEqualTo("분유 120ml 먹였어요");
        assertThat(completed.getVoiceInputId()).isNotNull();
        assertThat(completed.getAttemptCount()).isEqualTo(1);

        VoiceInput voiceInput = voiceInputRepository.findById(completed.getVoiceInputId()).orElseThrow();
        assertThat(voiceInput.getBabyId()).isEqualTo(1L);
        assertThat(voiceInput.getFamilyMemberId()).isEqualTo(7L);
        assertThat(voiceInput.getTranscript()).isEqualTo("분유 120ml 먹였어요");
        assertThat(voiceInput.getSttStatus()).isEqualTo(SttStatus.COMPLETED);

        List<ParentingRecord> records = parentingRecordRepository
                .findAllByBabyIdAndVoiceInputIdOrderByIdAsc(1L, voiceInput.getId());
        assertThat(records).hasSize(1);
        assertThat(records.getFirst().getRecordType()).isEqualTo(ParentingRecordType.FEEDING);
        assertThat(records.getFirst().getAmountMl()).isEqualTo(120);

        SttJobResponse fetched = sttJobService.get(1L, submitted.jobId());
        assertThat(fetched.status()).isEqualTo(SttStatus.COMPLETED);
        assertThat(fetched.parentingRecords()).hasSize(1);
    }
}
