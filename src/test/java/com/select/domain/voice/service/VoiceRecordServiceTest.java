package com.select.domain.voice.service;

import com.select.domain.voice.client.SttClient;
import com.select.domain.voice.client.dto.response.SttResponse;
import com.select.domain.voice.dto.request.VoiceRecordRequest;
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

    @Mock
    private VoiceInputRepository voiceInputRepository;

    @Mock
    private SttClient sttClient;

    @InjectMocks
    private VoiceRecordService voiceRecordService;

    @Test
    void transcribeReturnsTrimmedTranscriptWithoutSaving() {
        MultipartFile audio = new MockMultipartFile(
                "audio", "voice.mp3", "audio/mpeg", "audio".getBytes(StandardCharsets.UTF_8)
        );
        when(sttClient.transcribe(audio)).thenReturn(new SttResponse("  분유 먹였어  "));

        SttTranscriptResponse response = voiceRecordService.transcribe(1L, audio);

        assertThat(response.transcript()).isEqualTo("분유 먹였어");
        verify(sttClient).transcribe(audio);
        verifyNoInteractions(voiceInputRepository);
    }

    @Test
    void transcribeRejectsEmptyAudio() {
        MultipartFile audio = new MockMultipartFile(
                "audio", "voice.mp3", "audio/mpeg", new byte[0]
        );

        assertThatThrownBy(() -> voiceRecordService.transcribe(1L, audio))
                .isInstanceOf(InvalidAudioException.class);

        verifyNoInteractions(sttClient, voiceInputRepository);
    }

    @Test
    void transcribeRejectsUnsupportedAudioFormat() {
        MultipartFile audio = new MockMultipartFile(
                "audio", "voice.txt", "text/plain", "not-audio".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> voiceRecordService.transcribe(1L, audio))
                .isInstanceOf(InvalidAudioException.class);

        verifyNoInteractions(sttClient, voiceInputRepository);
    }

    @Test
    void transcribeRejectsAudioLargerThanLimit() {
        MultipartFile audio = mock(MultipartFile.class);
        when(audio.isEmpty()).thenReturn(false);
        when(audio.getSize()).thenReturn(AudioFileSupport.MAX_SIZE_BYTES + 1);

        assertThatThrownBy(() -> voiceRecordService.transcribe(1L, audio))
                .isInstanceOf(AudioTooLargeException.class);

        verifyNoInteractions(sttClient, voiceInputRepository);
    }

    @Test
    void transcribeFailsWhenSttReturnsBlankText() {
        MultipartFile audio = new MockMultipartFile(
                "audio", "voice.mp3", "audio/mpeg", "audio".getBytes(StandardCharsets.UTF_8)
        );
        when(sttClient.transcribe(audio)).thenReturn(new SttResponse("   "));

        assertThatThrownBy(() -> voiceRecordService.transcribe(1L, audio))
                .isInstanceOf(SttFailedException.class);

        verifyNoInteractions(voiceInputRepository);
    }

    @Test
    void saveStoresConfirmedTranscriptInVoiceInputs() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 25, 12, 0);
        VoiceInput saved = VoiceInput.builder()
                .babyId(1L)
                .familyMemberId(7L)
                .transcript("분유 먹였어")
                .sttStatus(SttStatus.COMPLETED)
                .build();
        ReflectionTestUtils.setField(saved, "id", 10L);
        ReflectionTestUtils.setField(saved, "createdAt", createdAt);
        when(voiceInputRepository.save(any(VoiceInput.class))).thenReturn(saved);

        VoiceInputResponse response = voiceRecordService.save(
                1L, new VoiceRecordRequest("  분유 먹였어  ", 7L)
        );

        ArgumentCaptor<VoiceInput> captor = ArgumentCaptor.forClass(VoiceInput.class);
        verify(voiceInputRepository).save(captor.capture());
        VoiceInput captured = captor.getValue();

        assertThat(captured.getBabyId()).isEqualTo(1L);
        assertThat(captured.getFamilyMemberId()).isEqualTo(7L);
        assertThat(captured.getAudioUrl()).isNull();
        assertThat(captured.getTranscript()).isEqualTo("분유 먹였어");
        assertThat(captured.getSttStatus()).isEqualTo(SttStatus.COMPLETED);

        assertThat(response.voiceInputId()).isEqualTo(10L);
        assertThat(response.babyId()).isEqualTo(1L);
        assertThat(response.familyMemberId()).isEqualTo(7L);
        assertThat(response.transcript()).isEqualTo("분유 먹였어");
        assertThat(response.sttStatus()).isEqualTo(SttStatus.COMPLETED);
        assertThat(response.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void getReturnsSavedVoiceInput() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 25, 12, 0);
        VoiceInput saved = VoiceInput.builder()
                .babyId(1L)
                .familyMemberId(7L)
                .transcript("분유 먹였어")
                .sttStatus(SttStatus.COMPLETED)
                .build();
        ReflectionTestUtils.setField(saved, "id", 10L);
        ReflectionTestUtils.setField(saved, "createdAt", createdAt);
        when(voiceInputRepository.findByIdAndBabyId(10L, 1L)).thenReturn(Optional.of(saved));

        VoiceInputResponse response = voiceRecordService.get(1L, 10L);

        assertThat(response.voiceInputId()).isEqualTo(10L);
        assertThat(response.transcript()).isEqualTo("분유 먹였어");
    }

    @Test
    void getFailsWhenVoiceInputDoesNotExist() {
        when(voiceInputRepository.findByIdAndBabyId(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> voiceRecordService.get(1L, 10L))
                .isInstanceOf(VoiceInputNotFoundException.class);
    }
}
