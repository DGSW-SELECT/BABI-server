package com.select.domain.voice.service;

import com.select.domain.baby.entity.Baby;
import com.select.domain.baby.entity.BabyMember;
import com.select.domain.baby.entity.BabyRole;
import com.select.domain.baby.exception.BabyNotFoundException;
import com.select.domain.baby.repository.BabyMemberRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.voice.client.SttClient;
import com.select.domain.voice.client.dto.response.SttResponse;
import com.select.domain.voice.dto.request.VoiceRecordRequest;
import com.select.domain.voice.dto.request.VoiceRecordUpdateRequest;
import com.select.domain.voice.dto.response.SttTranscriptResponse;
import com.select.domain.voice.dto.response.VoiceInputResponse;
import com.select.domain.voice.entity.SttStatus;
import com.select.domain.voice.entity.VoiceInput;
import com.select.domain.voice.exception.AudioTooLargeException;
import com.select.domain.voice.exception.InvalidAudioException;
import com.select.domain.voice.exception.SttFailedException;
import com.select.domain.voice.exception.VoiceInputNotFoundException;
import com.select.domain.voice.repository.VoiceInputRepository;
import com.select.domain.voice.support.AudioFileSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoiceRecordServiceTest {

    private static final Long BABY_ID = 1L;
    private static final Long USER_ID = 42L;
    private static final Long MEMBER_ID = 7L;

    @Mock
    private VoiceInputRepository voiceInputRepository;

    @Mock
    private SttClient sttClient;

    @Mock
    private BabyRepository babyRepository;

    @Mock
    private BabyMemberRepository babyMemberRepository;

    @InjectMocks
    private VoiceRecordService voiceRecordService;

    @BeforeEach
    void setUp() {
        Baby baby = Baby.builder()
                .name("아기")
                .birthDate(LocalDate.of(2025, 1, 15))
                .createdByUserId(USER_ID)
                .build();
        BabyMember member = BabyMember.builder()
                .babyId(BABY_ID)
                .userId(USER_ID)
                .role(BabyRole.OWNER)
                .build();
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        lenient().when(babyRepository.findById(BABY_ID)).thenReturn(Optional.of(baby));
        lenient().when(babyMemberRepository.findByBabyIdAndUserId(BABY_ID, USER_ID))
                .thenReturn(Optional.of(member));
    }

    @Test
    void transcribeReturnsTrimmedTranscriptWithoutSaving() {
        MultipartFile audio = new MockMultipartFile(
                "audio", "voice.mp3", "audio/mpeg", "audio".getBytes(StandardCharsets.UTF_8)
        );
        when(sttClient.transcribe(audio)).thenReturn(new SttResponse("  분유 먹였어  "));

        SttTranscriptResponse response = voiceRecordService.transcribe(BABY_ID, USER_ID, audio);

        assertThat(response.transcript()).isEqualTo("분유 먹였어");
        verify(sttClient).transcribe(audio);
        verifyNoInteractions(voiceInputRepository);
    }

    @Test
    void transcribeRejectsEmptyAudio() {
        MultipartFile audio = new MockMultipartFile(
                "audio", "voice.mp3", "audio/mpeg", new byte[0]
        );

        assertThatThrownBy(() -> voiceRecordService.transcribe(BABY_ID, USER_ID, audio))
                .isInstanceOf(InvalidAudioException.class);

        verifyNoInteractions(sttClient, voiceInputRepository);
    }

    @Test
    void transcribeRejectsUnsupportedAudioFormat() {
        MultipartFile audio = new MockMultipartFile(
                "audio", "voice.txt", "text/plain", "not-audio".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> voiceRecordService.transcribe(BABY_ID, USER_ID, audio))
                .isInstanceOf(InvalidAudioException.class);

        verifyNoInteractions(sttClient, voiceInputRepository);
    }

    @Test
    void transcribeRejectsAudioLargerThanLimit() {
        MultipartFile audio = mock(MultipartFile.class);
        when(audio.isEmpty()).thenReturn(false);
        when(audio.getSize()).thenReturn(AudioFileSupport.MAX_SIZE_BYTES + 1);

        assertThatThrownBy(() -> voiceRecordService.transcribe(BABY_ID, USER_ID, audio))
                .isInstanceOf(AudioTooLargeException.class);

        verifyNoInteractions(sttClient, voiceInputRepository);
    }

    @Test
    void transcribeFailsWhenSttReturnsBlankText() {
        MultipartFile audio = new MockMultipartFile(
                "audio", "voice.mp3", "audio/mpeg", "audio".getBytes(StandardCharsets.UTF_8)
        );
        when(sttClient.transcribe(audio)).thenReturn(new SttResponse("   "));

        assertThatThrownBy(() -> voiceRecordService.transcribe(BABY_ID, USER_ID, audio))
                .isInstanceOf(SttFailedException.class);

        verifyNoInteractions(voiceInputRepository);
    }

    @Test
    void transcribeFailsWhenBabyDoesNotExist() {
        MultipartFile audio = new MockMultipartFile(
                "audio", "voice.mp3", "audio/mpeg", "audio".getBytes(StandardCharsets.UTF_8)
        );
        when(babyRepository.findById(BABY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> voiceRecordService.transcribe(BABY_ID, USER_ID, audio))
                .isInstanceOf(BabyNotFoundException.class);

        verifyNoInteractions(sttClient, voiceInputRepository);
    }

    @Test
    void saveStoresConfirmedTranscriptInVoiceInputs() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 25, 12, 0);
        VoiceInput saved = VoiceInput.builder()
                .babyId(BABY_ID)
                .familyMemberId(MEMBER_ID)
                .transcript("분유 먹였어")
                .sttStatus(SttStatus.COMPLETED)
                .build();
        ReflectionTestUtils.setField(saved, "id", 10L);
        ReflectionTestUtils.setField(saved, "createdAt", createdAt);
        ReflectionTestUtils.setField(saved, "updatedAt", createdAt);
        when(voiceInputRepository.save(any(VoiceInput.class))).thenReturn(saved);

        VoiceInputResponse response = voiceRecordService.save(
                BABY_ID, USER_ID, new VoiceRecordRequest("  분유 먹였어  ")
        );

        ArgumentCaptor<VoiceInput> captor = ArgumentCaptor.forClass(VoiceInput.class);
        verify(voiceInputRepository).save(captor.capture());
        VoiceInput captured = captor.getValue();

        assertThat(captured.getBabyId()).isEqualTo(BABY_ID);
        assertThat(captured.getFamilyMemberId()).isEqualTo(MEMBER_ID);
        assertThat(captured.getAudioUrl()).isNull();
        assertThat(captured.getTranscript()).isEqualTo("분유 먹였어");
        assertThat(captured.getSttStatus()).isEqualTo(SttStatus.COMPLETED);

        assertThat(response.voiceInputId()).isEqualTo(10L);
        assertThat(response.babyId()).isEqualTo(BABY_ID);
        assertThat(response.familyMemberId()).isEqualTo(MEMBER_ID);
        assertThat(response.transcript()).isEqualTo("분유 먹였어");
        assertThat(response.sttStatus()).isEqualTo(SttStatus.COMPLETED);
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.updatedAt()).isEqualTo(createdAt);
    }

    @Test
    void saveFailsWhenUserIsNotMember() {
        when(babyMemberRepository.findByBabyIdAndUserId(BABY_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> voiceRecordService.save(
                BABY_ID, USER_ID, new VoiceRecordRequest("분유 먹였어")
        )).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(voiceInputRepository);
    }

    @Test
    void getReturnsSavedVoiceInput() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 25, 12, 0);
        VoiceInput saved = VoiceInput.builder()
                .babyId(BABY_ID)
                .familyMemberId(MEMBER_ID)
                .transcript("분유 먹였어")
                .sttStatus(SttStatus.COMPLETED)
                .build();
        ReflectionTestUtils.setField(saved, "id", 10L);
        ReflectionTestUtils.setField(saved, "createdAt", createdAt);
        ReflectionTestUtils.setField(saved, "updatedAt", createdAt);
        when(voiceInputRepository.findByIdAndBabyId(10L, BABY_ID)).thenReturn(Optional.of(saved));

        VoiceInputResponse response = voiceRecordService.get(BABY_ID, USER_ID, 10L);

        assertThat(response.voiceInputId()).isEqualTo(10L);
        assertThat(response.transcript()).isEqualTo("분유 먹였어");
    }

    @Test
    void getFailsWhenVoiceInputDoesNotExist() {
        when(voiceInputRepository.findByIdAndBabyId(10L, BABY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> voiceRecordService.get(BABY_ID, USER_ID, 10L))
                .isInstanceOf(VoiceInputNotFoundException.class);
    }

    @Test
    void updateChangesTranscriptAndReturnsUpdatedVoiceInput() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 25, 12, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 10, 26, 9, 30);
        VoiceInput saved = VoiceInput.builder()
                .babyId(BABY_ID)
                .familyMemberId(MEMBER_ID)
                .transcript("분유 먹였어")
                .sttStatus(SttStatus.COMPLETED)
                .build();
        ReflectionTestUtils.setField(saved, "id", 10L);
        ReflectionTestUtils.setField(saved, "createdAt", createdAt);
        ReflectionTestUtils.setField(saved, "updatedAt", createdAt);
        when(voiceInputRepository.findByIdAndBabyId(10L, BABY_ID)).thenReturn(Optional.of(saved));
        when(voiceInputRepository.saveAndFlush(saved)).thenAnswer(invocation -> {
            ReflectionTestUtils.setField(saved, "updatedAt", updatedAt);
            return saved;
        });

        VoiceInputResponse response = voiceRecordService.update(
                BABY_ID,
                USER_ID,
                10L,
                new VoiceRecordUpdateRequest("  수정된 분유 기록  ")
        );

        assertThat(saved.getTranscript()).isEqualTo("수정된 분유 기록");
        assertThat(response.voiceInputId()).isEqualTo(10L);
        assertThat(response.transcript()).isEqualTo("수정된 분유 기록");
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
        verify(voiceInputRepository).saveAndFlush(saved);
    }

    @Test
    void updateFailsWhenVoiceInputDoesNotExist() {
        when(voiceInputRepository.findByIdAndBabyId(10L, BABY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> voiceRecordService.update(
                BABY_ID,
                USER_ID,
                10L,
                new VoiceRecordUpdateRequest("수정된 분유 기록")
        )).isInstanceOf(VoiceInputNotFoundException.class);

        verify(voiceInputRepository, never()).saveAndFlush(any(VoiceInput.class));
    }
}
