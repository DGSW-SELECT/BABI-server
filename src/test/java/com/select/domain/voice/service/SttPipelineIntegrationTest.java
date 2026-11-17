package com.select.domain.voice.service;

import com.select.domain.baby.entity.Baby;
import com.select.domain.baby.entity.BabyMember;
import com.select.domain.baby.entity.BabyRole;
import com.select.domain.baby.repository.BabyMemberRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.care.dto.response.CareRecordResponse;
import com.select.domain.care.entity.CareRecordType;
import com.select.domain.care.repository.CareRecordRepository;
import com.select.domain.user.entity.User;
import com.select.domain.user.repository.UserRepository;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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

    private static final String EMAIL = "stt-pipeline@example.invalid";

    @Autowired
    private SttJobService sttJobService;

    @Autowired
    private SttJobRepository sttJobRepository;

    @Autowired
    private VoiceInputRepository voiceInputRepository;

    @Autowired
    private CareRecordRepository careRecordRepository;

    @Autowired
    private BabyMemberRepository babyMemberRepository;

    @Autowired
    private BabyRepository babyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AudioStorage audioStorage;

    @Autowired
    private SttJobProperties sttJobProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private SttClient sttClient;

    private SttJobWorker sttJobWorker;
    private Baby baby;
    private User user;

    @BeforeEach
    void setUp() {
        cleanUp();

        user = userRepository.save(User.builder()
                .email(EMAIL)
                .passwordHash("test-password-hash")
                .nickname("테스트 보호자")
                .build());
        baby = babyRepository.save(Baby.builder()
                .name("테스트 아기")
                .birthDate(LocalDate.of(2026, 1, 1))
                .createdByUserId(user.getId())
                .build());
        babyMemberRepository.save(BabyMember.builder()
                .babyId(baby.getId())
                .userId(user.getId())
                .role(BabyRole.OWNER)
                .build());

        sttJobWorker = new SttJobWorker(sttJobService, sttClient, audioStorage, sttJobProperties);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        jdbcTemplate.update("delete from feeding_records");
        jdbcTemplate.update("delete from sleep_records");
        jdbcTemplate.update("delete from temperature_records");
        jdbcTemplate.update("delete from diaper_records");
        jdbcTemplate.update("delete from cry_records");
        jdbcTemplate.update("delete from growth_records");
        jdbcTemplate.update("delete from voice_inputs");
        jdbcTemplate.update("delete from stt_jobs");
        jdbcTemplate.update("delete from baby_members");
        jdbcTemplate.update("delete from babies");
        jdbcTemplate.update("delete from users");
    }

    @Test
    void submittedJobIsTranscribedAndSavedOnlyAfterConfirmation() {
        when(sttClient.transcribe(any(MultipartFile.class)))
                .thenReturn(new SttResponse("분유 120ml 먹였어요"));
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "voice.mp3", "audio/mpeg", "audio".getBytes(StandardCharsets.UTF_8)
        );

        SttJobResponse submitted = sttJobService.submit(baby.getId(), user.getId(), audio);

        assertThat(submitted.status()).isEqualTo(SttStatus.PENDING);
        assertThat(sttJobService.claim(submitted.jobId())).isTrue();

        sttJobWorker.process(submitted.jobId());

        SttJob completed = sttJobRepository.findById(submitted.jobId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(SttStatus.COMPLETED);
        assertThat(completed.getTranscript()).isEqualTo("분유 120ml 먹였어요");
        assertThat(completed.getVoiceInputId()).isNull();
        assertThat(completed.getAttemptCount()).isEqualTo(1);
        assertThat(voiceInputRepository.count()).isZero();

        SttJobResponse confirmed = sttJobService.confirm(
                baby.getId(), user.getId(), submitted.jobId()
        );

        assertThat(confirmed.status()).isEqualTo(SttStatus.COMPLETED);
        assertThat(confirmed.voiceInputId()).isNotNull();
        assertThat(confirmed.careRecords()).hasSize(1);

        VoiceInput voiceInput = voiceInputRepository
                .findById(confirmed.voiceInputId())
                .orElseThrow();
        assertThat(voiceInput.getBabyId()).isEqualTo(baby.getId());
        assertThat(voiceInput.getFamilyMemberId()).isNotNull();
        assertThat(voiceInput.getTranscript()).isEqualTo("분유 120ml 먹였어요");
        assertThat(voiceInput.getSttStatus()).isEqualTo(SttStatus.COMPLETED);

        List<CareRecordResponse> records = careRecordRepository.findDay(
                baby.getId(), LocalDate.now(), null, null, 10, 0
        );
        assertThat(records).hasSize(1);
        assertThat(records.getFirst().recordType()).isEqualTo(CareRecordType.FEEDING);
        assertThat(records.getFirst().amountMl()).isEqualTo(120);

        SttJobResponse fetched = sttJobService.get(baby.getId(), user.getId(), submitted.jobId());
        assertThat(fetched.status()).isEqualTo(SttStatus.COMPLETED);
        assertThat(fetched.voiceInputId()).isEqualTo(voiceInput.getId());
    }
}
