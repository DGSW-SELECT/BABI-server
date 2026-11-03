package com.select.domain.voice.service;

import com.select.domain.parenting.dto.response.ParentingRecordResponse;
import com.select.domain.parenting.service.ParentingRecordService;
import com.select.domain.voice.client.SttClient;
import com.select.domain.voice.client.dto.response.SttResponse;
import com.select.domain.voice.dto.request.VoiceRecordRequest;
import com.select.domain.voice.dto.response.SttTranscriptResponse;
import com.select.domain.voice.dto.response.VoiceInputResponse;
import com.select.domain.voice.entity.SttStatus;
import com.select.domain.voice.entity.VoiceInput;
import com.select.domain.voice.exception.SttFailedException;
import com.select.domain.voice.exception.VoiceInputNotFoundException;
import com.select.domain.voice.repository.VoiceInputRepository;
import com.select.domain.voice.support.AudioFileSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class VoiceRecordService {

    private final VoiceInputRepository voiceInputRepository;
    private final SttClient sttClient;
    private final ParentingRecordService parentingRecordService;

    public VoiceRecordService(
            VoiceInputRepository voiceInputRepository,
            SttClient sttClient,
            ParentingRecordService parentingRecordService
    ) {
        this.voiceInputRepository = voiceInputRepository;
        this.sttClient = sttClient;
        this.parentingRecordService = parentingRecordService;
    }

    public SttTranscriptResponse transcribe(Long babyId, MultipartFile audio) {
        AudioFileSupport.validate(audio);
        SttResponse response = sttClient.transcribe(audio);
        String transcript = response == null ? null : response.text();
        if (!StringUtils.hasText(transcript)) {
            throw new SttFailedException("음성을 텍스트로 변환하지 못했습니다.");
        }
        return new SttTranscriptResponse(transcript.trim());
    }

    @Transactional
    public VoiceInputResponse save(Long babyId, VoiceRecordRequest request) {
        VoiceInput voiceInput = VoiceInput.builder()
                .babyId(babyId)
                .familyMemberId(request.familyMemberId())
                .audioUrl(null)
                .transcript(request.transcript().trim())
                .sttStatus(SttStatus.COMPLETED)
                .build();

        VoiceInput saved = voiceInputRepository.save(voiceInput);
        List<ParentingRecordResponse> parentingRecords = parentingRecordService.analyzeAndSave(
                saved.getBabyId(),
                saved.getFamilyMemberId(),
                saved.getId(),
                saved.getTranscript(),
                saved.getCreatedAt()
        );

        return VoiceInputResponse.of(saved, parentingRecords);
    }

    @Transactional(readOnly = true)
    public VoiceInputResponse get(Long babyId, Long voiceInputId) {
        VoiceInput voiceInput = voiceInputRepository.findByIdAndBabyId(voiceInputId, babyId)
                .orElseThrow(VoiceInputNotFoundException::new);
        List<ParentingRecordResponse> parentingRecords =
                parentingRecordService.findByVoiceInput(babyId, voiceInputId);

        return VoiceInputResponse.of(voiceInput, parentingRecords);
    }
}
