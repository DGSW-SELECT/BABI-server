package com.select.domain.voice.service;

import com.select.domain.baby.entity.Baby;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.family.entity.FamilyMember;
import com.select.domain.family.repository.FamilyMemberRepository;
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
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 실제 DB·파일 저장소를 사용해 제출 -> 선점 -> STT -> 확인 저장 흐름을 검증한다.
 */
@SpringBootTest
class SttPipelineIntegrationTest {

    private static final Long USER_ID = 42L;
    private static final Long FAMILY_GROUP_ID = 100L;

    @Autowired
    private SttJobService sttJobService;

    @Autowired
    private SttJobRepository sttJobRepository;

    @Autowired
    private VoiceInputRepository voiceInputRepository;

    @Autowired
    private ParentingRecordRepository parentingRecordRepository;

    @Autowired
    private FamilyMemberRepository familyMemberRepository;

    @Autowired
    private BabyRepository babyRepository;

    @Autowired
    private AudioStorage audioStorage;

    @Autowired
    private SttJobProperties sttJobProperties;

    @MockitoBean
    private SttClient sttClient;

    private SttJobWorker sttJobWorker;
    private Baby baby;

    @BeforeEach
    void setUp() {
        parentingRecordRepository.deleteAll();
        voiceInputRepository.deleteAll();
        sttJobRepository.deleteAll();
        babyRepository.deleteAll();
        familyMemberRepository.deleteAll();

        familyMemberRepository.save(FamilyMember.builder()
                .familyGroupId(FAMILY_GROUP_ID)
                .userId(USER_ID)
                .role("OWNER")
                .status(FamilyMember.STATUS_ACTIVE)
                .build());
        baby = babyRepository.save(Baby.builder()
                .familyGroupId(FAMILY_GROUP_ID)
                .name("테스트 아기")
                .birthDate(LocalDate.of(2026, 1, 1))
                .build());

        sttJobWorker = new SttJobWorker(sttJobService, sttClient, audioStorage, sttJobProperties);
    }

    @Test
    void submittedJobIsTranscribedAndSavedOnlyAfterConfirmation() {
        when(sttClient.transcribe(any(MultipartFile.class)))
                .thenReturn(new SttResponse("분유 120ml 먹였어요"));
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "voice.mp3", "audio/mpeg", "audio".getBytes(StandardCharsets.UTF_8)
        );

        SttJobResponse submitted = sttJobService.submit(baby.getId(), USER_ID, audio);

        assertThat(submitted.status()).isEqualTo(SttStatus.PENDING);
        assertThat(sttJobService.claim(submitted.jobId())).isTrue();

        sttJobWorker.process(submitted.jobId());

        SttJob completed = sttJobRepository.findById(submitted.jobId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(SttStatus.COMPLETED);
        assertThat(completed.getTranscript()).isEqualTo("분유 120ml 먹였어요");
        assertThat(completed.getVoiceInputId()).isNull();
        assertThat(completed.getAttemptCount()).isEqualTo(1);
        assertThat(voiceInputRepository.count()).isZero();
        assertThat(parentingRecordRepository.count()).isZero();

        SttJobResponse confirmed = sttJobService.confirm(baby.getId(), USER_ID, submitted.jobId());

        assertThat(confirmed.status()).isEqualTo(SttStatus.COMPLETED);
        assertThat(confirmed.voiceInputId()).isNotNull();
        assertThat(confirmed.parentingRecords()).hasSize(1);

        VoiceInput voiceInput = voiceInputRepository.findById(confirmed.voiceInputId()).orElseThrow();
        assertThat(voiceInput.getBabyId()).isEqualTo(baby.getId());
        assertThat(voiceInput.getFamilyMemberId()).isNotNull();
        assertThat(voiceInput.getTranscript()).isEqualTo("분유 120ml 먹였어요");
        assertThat(voiceInput.getSttStatus()).isEqualTo(SttStatus.COMPLETED);

        List<ParentingRecord> records = parentingRecordRepository
                .findAllByBabyIdAndVoiceInputIdOrderByIdAsc(baby.getId(), voiceInput.getId());
        assertThat(records).hasSize(1);
        assertThat(records.getFirst().getRecordType()).isEqualTo(ParentingRecordType.FEEDING);
        assertThat(records.getFirst().getAmountMl()).isEqualTo(120);

        SttJobResponse fetched = sttJobService.get(baby.getId(), USER_ID, submitted.jobId());
        assertThat(fetched.status()).isEqualTo(SttStatus.COMPLETED);
        assertThat(fetched.voiceInputId()).isEqualTo(voiceInput.getId());
        assertThat(fetched.parentingRecords()).hasSize(1);
    }
}
