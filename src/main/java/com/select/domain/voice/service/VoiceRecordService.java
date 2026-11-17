package com.select.domain.voice.service;

import com.select.domain.baby.entity.BabyMember;
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
import com.select.domain.voice.exception.SttFailedException;
import com.select.domain.voice.exception.VoiceInputNotFoundException;
import com.select.domain.voice.repository.VoiceInputRepository;
import com.select.domain.voice.support.AudioFileSupport;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VoiceRecordService {

    private final VoiceInputRepository voiceInputRepository;
    private final SttClient sttClient;
    private final BabyRepository babyRepository;
    private final BabyMemberRepository babyMemberRepository;

    public VoiceRecordService(
            VoiceInputRepository voiceInputRepository,
            SttClient sttClient,
            BabyRepository babyRepository,
            BabyMemberRepository babyMemberRepository
    ) {
        this.voiceInputRepository = voiceInputRepository;
        this.sttClient = sttClient;
        this.babyRepository = babyRepository;
        this.babyMemberRepository = babyMemberRepository;
    }

    public SttTranscriptResponse transcribe(Long babyId, Long userId, MultipartFile audio) {
        requireMember(babyId, userId);
        AudioFileSupport.validate(audio);
        SttResponse response = sttClient.transcribe(audio);
        String transcript = response == null ? null : response.text();
        if (!StringUtils.hasText(transcript)) {
            throw new SttFailedException("음성을 텍스트로 변환하지 못했습니다.");
        }
        return new SttTranscriptResponse(transcript.trim());
    }

    @Transactional
    public VoiceInputResponse save(Long babyId, Long userId, VoiceRecordRequest request) {
        BabyMember member = requireMember(babyId, userId);
        VoiceInput voiceInput = VoiceInput.builder()
                .babyId(babyId)
                .familyMemberId(member.getId())
                .audioUrl(null)
                .transcript(request.transcript().trim())
                .sttStatus(SttStatus.COMPLETED)
                .build();
        return VoiceInputResponse.from(voiceInputRepository.save(voiceInput));
    }

    @Transactional(readOnly = true)
    public VoiceInputResponse get(Long babyId, Long userId, Long voiceInputId) {
        requireMember(babyId, userId);
        return voiceInputRepository.findByIdAndBabyId(voiceInputId, babyId)
                .map(VoiceInputResponse::from)
                .orElseThrow(VoiceInputNotFoundException::new);
    }

    @Transactional
    public VoiceInputResponse update(
            Long babyId,
            Long userId,
            Long voiceInputId,
            VoiceRecordUpdateRequest request
    ) {
        requireMember(babyId, userId);
        VoiceInput voiceInput = voiceInputRepository.findByIdAndBabyId(voiceInputId, babyId)
                .orElseThrow(VoiceInputNotFoundException::new);
        voiceInput.updateTranscript(request.transcript().trim());

        return VoiceInputResponse.from(voiceInputRepository.saveAndFlush(voiceInput));
    }

    private BabyMember requireMember(Long babyId, Long userId) {
        babyRepository.findById(babyId)
                .orElseThrow(BabyNotFoundException::new);
        return babyMemberRepository.findByBabyIdAndUserId(babyId, userId)
                .orElseThrow(() -> new AccessDeniedException("해당 아기의 구성원이 아닙니다."));
    }
}
