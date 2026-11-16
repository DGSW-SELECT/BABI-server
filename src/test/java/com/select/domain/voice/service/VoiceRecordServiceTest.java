package com.select.domain.voice.service;

import com.select.domain.baby.service.AccountContextService;
import com.select.domain.parenting.analyzer.ParentingRecordAnalyzer;
import com.select.domain.parenting.analyzer.ParentingRecordDraft;
import com.select.domain.parenting.dto.response.ParentingRecordResponse;
import com.select.domain.parenting.entity.FeedingType;
import com.select.domain.parenting.entity.ParentingRecordType;
import com.select.domain.parenting.service.ParentingRecordService;
import com.select.domain.voice.client.SttClient;
import com.select.domain.voice.client.dto.response.SttResponse;
import com.select.domain.voice.dto.request.ParsedVoiceRecordRequest;
import com.select.domain.voice.dto.request.VoiceRecordRequest;
import com.select.domain.voice.dto.response.ParsedVoiceRecordResponse;
import com.select.domain.voice.dto.response.SttTranscriptResponse;
import com.select.domain.voice.dto.response.VoiceInputResponse;
import com.select.domain.voice.dto.response.VoiceRecordSavedResponse;
import com.select.domain.voice.entity.SttStatus;
import com.select.domain.voice.entity.VoiceInput;
import com.select.domain.voice.exception.AudioTooLargeException;
import com.select.domain.voice.exception.InvalidAudioException;
import com.select.domain.voice.exception.SttFailedException;
import com.select.domain.voice.exception.VoiceInputNotFoundException;
import com.select.domain.voice.repository.VoiceInputRepository;
import com.select.domain.voice.support.AudioFileSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoiceRecordServiceTest {

    private static final Long BABY_ID = 1L;
    private static final Long FAMILY_MEMBER_ID = 7L;
    private static final Long USER_ID = 42L;

    @Mock
    private VoiceInputRepository voiceInputRepository;

    @Mock
    private SttClient sttClient;

    @Mock
    private ParentingRecordService parentingRecordService;

    @Mock
    private ParentingRecordAnalyzer parentingRecordAnalyzer;

    @Mock
    private AccountContextService accountContextService;

    @InjectMocks
    private VoiceRecordService voiceRecordService;

    @Test
    void transcribeReturnsTrimmedTranscriptWithoutSaving() {
        MultipartFile audio = audio();
        when(accountContextService.resolve(USER_ID, BABY_ID))
                .thenReturn(new AccountContextService.AccountContext(BABY_ID, FAMILY_MEMBER_ID));
        when(sttClient.transcribe(audio)).thenReturn(new SttResponse("  분유 먹였어  "));

        SttTranscriptResponse response = voiceRecordService.transcribe(BABY_ID, USER_ID, audio);

        assertThat(response.transcript()).isEqualTo("분유 먹였어");
        verify(sttClient).transcribe(audio);
        verifyNoInteractions(voiceInputRepository, parentingRecordService);
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
        MultipartFile audio = audio();
        when(accountContextService.resolve(USER_ID, BABY_ID))
                .thenReturn(new AccountContextService.AccountContext(BABY_ID, FAMILY_MEMBER_ID));
        when(sttClient.transcribe(audio)).thenReturn(new SttResponse("   "));

        assertThatThrownBy(() -> voiceRecordService.transcribe(BABY_ID, USER_ID, audio))
                .isInstanceOf(SttFailedException.class);

        verifyNoInteractions(voiceInputRepository);
    }

    @Test
    void parseReturnsTranscriptAndItemsWithoutSaving() {
        MultipartFile audio = audio();
        when(accountContextService.resolve(USER_ID, BABY_ID))
                .thenReturn(new AccountContextService.AccountContext(BABY_ID, FAMILY_MEMBER_ID));
        when(sttClient.transcribe(audio))
                .thenReturn(new SttResponse("분유 120ml 먹였고 30분 잤어"));
        when(parentingRecordAnalyzer.analyze(any(), any(LocalDateTime.class)))
                .thenReturn(List.of(
                        new ParentingRecordDraft(
                                ParentingRecordType.FEEDING,
                                LocalDateTime.of(2026, 11, 16, 10, 0),
                                120,
                                null,
                                FeedingType.FORMULA,
                                null,
                                "분유 120ml"
                        ),
                        new ParentingRecordDraft(
                                ParentingRecordType.SLEEP,
                                LocalDateTime.of(2026, 11, 16, 10, 0),
                                null,
                                30,
                                null,
                                null,
                                "30분 잤어"
                        )
                ));

        ParsedVoiceRecordResponse response = voiceRecordService.parse(USER_ID, BABY_ID, audio);

        assertThat(response.transcript()).isEqualTo("분유 120ml 먹였고 30분 잤어");
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).type()).isEqualTo("feeding");
        assertThat(response.items().get(0).label()).contains("분유", "120ml");
        assertThat(response.items().get(1).type()).isEqualTo("sleep");
        assertThat(response.items().get(1).label()).contains("30분");
        verifyNoInteractions(voiceInputRepository, parentingRecordService);
    }

    @Test
    void saveStoresConfirmedTranscriptInVoiceInputs() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 25, 12, 0);
        VoiceInput saved = savedVoiceInput(createdAt);
        when(accountContextService.resolve(USER_ID, BABY_ID))
                .thenReturn(new AccountContextService.AccountContext(BABY_ID, FAMILY_MEMBER_ID));
        when(voiceInputRepository.save(any(VoiceInput.class))).thenReturn(saved);

        ParentingRecordResponse parentingRecord = parentingRecord(createdAt);
        when(parentingRecordService.analyzeAndSave(
                BABY_ID, FAMILY_MEMBER_ID, 10L, "분유 먹였어", createdAt
        )).thenReturn(List.of(parentingRecord));

        VoiceInputResponse response = voiceRecordService.save(
                BABY_ID, USER_ID, new VoiceRecordRequest("  분유 먹였어  ")
        );

        ArgumentCaptor<VoiceInput> captor = ArgumentCaptor.forClass(VoiceInput.class);
        verify(voiceInputRepository).save(captor.capture());
        VoiceInput captured = captor.getValue();

        assertThat(captured.getBabyId()).isEqualTo(BABY_ID);
        assertThat(captured.getFamilyMemberId()).isEqualTo(FAMILY_MEMBER_ID);
        assertThat(captured.getAudioUrl()).isNull();
        assertThat(captured.getTranscript()).isEqualTo("분유 먹였어");
        assertThat(captured.getSttStatus()).isEqualTo(SttStatus.COMPLETED);

        assertThat(response.voiceInputId()).isEqualTo(10L);
        assertThat(response.babyId()).isEqualTo(BABY_ID);
        assertThat(response.familyMemberId()).isEqualTo(FAMILY_MEMBER_ID);
        assertThat(response.transcript()).isEqualTo("분유 먹였어");
        assertThat(response.sttStatus()).isEqualTo(SttStatus.COMPLETED);
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.parentingRecords()).containsExactly(parentingRecord);
    }

    @Test
    void saveParsedResolvesSingleBabyAndStoresRecord() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 25, 12, 0);
        VoiceInput saved = savedVoiceInput(createdAt);
        when(accountContextService.resolve(USER_ID, null))
                .thenReturn(new AccountContextService.AccountContext(BABY_ID, FAMILY_MEMBER_ID));
        when(voiceInputRepository.save(any(VoiceInput.class))).thenReturn(saved);

        ParentingRecordResponse parentingRecord = parentingRecord(createdAt);
        when(parentingRecordService.analyzeAndSave(
                BABY_ID, FAMILY_MEMBER_ID, 10L, "분유 먹였어", createdAt
        )).thenReturn(List.of(parentingRecord));

        VoiceRecordSavedResponse response = voiceRecordService.saveParsed(
                USER_ID,
                null,
                new ParsedVoiceRecordRequest("분유 먹였어", List.of())
        );

        assertThat(response.ok()).isTrue();
        assertThat(response.voiceInputId()).isEqualTo(10L);
        assertThat(response.parentingRecords()).containsExactly(parentingRecord);
        verify(accountContextService).resolve(USER_ID, null);
    }

    @Test
    void getReturnsSavedVoiceInput() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 25, 12, 0);
        VoiceInput saved = savedVoiceInput(createdAt);
        when(accountContextService.resolve(USER_ID, BABY_ID))
                .thenReturn(new AccountContextService.AccountContext(BABY_ID, FAMILY_MEMBER_ID));
        when(voiceInputRepository.findByIdAndBabyId(10L, BABY_ID)).thenReturn(Optional.of(saved));

        ParentingRecordResponse parentingRecord = parentingRecord(createdAt);
        when(parentingRecordService.findByVoiceInput(BABY_ID, 10L))
                .thenReturn(List.of(parentingRecord));

        VoiceInputResponse response = voiceRecordService.get(BABY_ID, USER_ID, 10L);

        assertThat(response.voiceInputId()).isEqualTo(10L);
        assertThat(response.transcript()).isEqualTo("분유 먹였어");
        assertThat(response.parentingRecords()).containsExactly(parentingRecord);
    }

    @Test
    void getFailsWhenVoiceInputDoesNotExist() {
        when(accountContextService.resolve(USER_ID, BABY_ID))
                .thenReturn(new AccountContextService.AccountContext(BABY_ID, FAMILY_MEMBER_ID));
        when(voiceInputRepository.findByIdAndBabyId(10L, BABY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> voiceRecordService.get(BABY_ID, USER_ID, 10L))
                .isInstanceOf(VoiceInputNotFoundException.class);

        verifyNoInteractions(parentingRecordService);
    }

    private static MultipartFile audio() {
        return new MockMultipartFile(
                "audio", "voice.mp3", "audio/mpeg", "audio".getBytes(StandardCharsets.UTF_8)
        );
    }

    private static VoiceInput savedVoiceInput(LocalDateTime createdAt) {
        VoiceInput saved = VoiceInput.builder()
                .babyId(BABY_ID)
                .familyMemberId(FAMILY_MEMBER_ID)
                .transcript("분유 먹였어")
                .sttStatus(SttStatus.COMPLETED)
                .build();
        ReflectionTestUtils.setField(saved, "id", 10L);
        ReflectionTestUtils.setField(saved, "createdAt", createdAt);
        return saved;
    }

    private static ParentingRecordResponse parentingRecord(LocalDateTime createdAt) {
        return new ParentingRecordResponse(
                100L, BABY_ID, FAMILY_MEMBER_ID, 10L, ParentingRecordType.FEEDING,
                LocalDateTime.of(2026, 10, 25, 12, 0), 100, null,
                FeedingType.FORMULA, null, "분유 먹였어", createdAt
        );
    }
}
