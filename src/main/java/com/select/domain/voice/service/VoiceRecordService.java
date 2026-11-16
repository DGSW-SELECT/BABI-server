package com.select.domain.voice.service;

import com.select.domain.baby.service.AccountContextService;
import com.select.domain.parenting.analyzer.ParentingRecordAnalyzer;
import com.select.domain.parenting.analyzer.ParentingRecordDraft;
import com.select.domain.parenting.dto.response.ParentingRecordResponse;
import com.select.domain.parenting.entity.DiaperType;
import com.select.domain.parenting.entity.FeedingType;
import com.select.domain.parenting.service.ParentingRecordService;
import com.select.domain.voice.client.SttClient;
import com.select.domain.voice.client.dto.response.SttResponse;
import com.select.domain.voice.dto.request.ParsedVoiceRecordRequest;
import com.select.domain.voice.dto.request.VoiceRecordRequest;
import com.select.domain.voice.dto.response.ParsedVoiceItemResponse;
import com.select.domain.voice.dto.response.ParsedVoiceRecordResponse;
import com.select.domain.voice.dto.response.SttTranscriptResponse;
import com.select.domain.voice.dto.response.VoiceInputResponse;
import com.select.domain.voice.dto.response.VoiceRecordSavedResponse;
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

import java.time.LocalDateTime;
import java.util.List;

@Service
public class VoiceRecordService {

    private final VoiceInputRepository voiceInputRepository;
    private final SttClient sttClient;
    private final ParentingRecordService parentingRecordService;
    private final ParentingRecordAnalyzer parentingRecordAnalyzer;
    private final AccountContextService accountContextService;

    public VoiceRecordService(
            VoiceInputRepository voiceInputRepository,
            SttClient sttClient,
            ParentingRecordService parentingRecordService,
            ParentingRecordAnalyzer parentingRecordAnalyzer,
            AccountContextService accountContextService
    ) {
        this.voiceInputRepository = voiceInputRepository;
        this.sttClient = sttClient;
        this.parentingRecordService = parentingRecordService;
        this.parentingRecordAnalyzer = parentingRecordAnalyzer;
        this.accountContextService = accountContextService;
    }

    /**
     * 기존 경로 기반 API용 STT 미리보기. 저장하지 않는다.
     */
    public SttTranscriptResponse transcribe(Long babyId, Long userId, MultipartFile audio) {
        accountContextService.resolve(userId, babyId);
        return new SttTranscriptResponse(transcribeAudio(audio));
    }

    /**
     * 프론트 음성 기록 화면용 STT 미리보기. 저장하지 않는다.
     */
    public ParsedVoiceRecordResponse parse(Long userId, Long babyId, MultipartFile audio) {
        accountContextService.resolve(userId, babyId);
        String transcript = transcribeAudio(audio);
        List<ParsedVoiceItemResponse> items = parentingRecordAnalyzer
                .analyze(transcript, LocalDateTime.now())
                .stream()
                .map(this::toItem)
                .toList();
        return new ParsedVoiceRecordResponse(transcript, items);
    }

    /**
     * 사용자가 확인한 transcript를 저장한다.
     */
    @Transactional
    public VoiceInputResponse save(Long babyId, Long userId, VoiceRecordRequest request) {
        AccountContextService.AccountContext context = accountContextService.resolve(userId, babyId);
        SavedVoiceRecord saved = saveConfirmed(context, request.transcript());
        return VoiceInputResponse.of(saved.voiceInput(), saved.parentingRecords());
    }

    /**
     * 프론트 음성 기록 화면에서 확인한 transcript를 저장한다.
     */
    @Transactional
    public VoiceRecordSavedResponse saveParsed(
            Long userId,
            Long babyId,
            ParsedVoiceRecordRequest request
    ) {
        AccountContextService.AccountContext context = accountContextService.resolve(userId, babyId);
        SavedVoiceRecord saved = saveConfirmed(context, request.transcript());
        return new VoiceRecordSavedResponse(
                true,
                saved.voiceInput().getId(),
                saved.parentingRecords()
        );
    }

    @Transactional(readOnly = true)
    public VoiceInputResponse get(Long babyId, Long userId, Long voiceInputId) {
        AccountContextService.AccountContext context = accountContextService.resolve(userId, babyId);
        VoiceInput voiceInput = voiceInputRepository.findByIdAndBabyId(voiceInputId, context.babyId())
                .orElseThrow(VoiceInputNotFoundException::new);
        List<ParentingRecordResponse> parentingRecords =
                parentingRecordService.findByVoiceInput(context.babyId(), voiceInputId);

        return VoiceInputResponse.of(voiceInput, parentingRecords);
    }

    private SavedVoiceRecord saveConfirmed(
            AccountContextService.AccountContext context,
            String transcript
    ) {
        VoiceInput voiceInput = VoiceInput.builder()
                .babyId(context.babyId())
                .familyMemberId(context.familyMemberId())
                .audioUrl(null)
                .transcript(transcript.trim())
                .sttStatus(SttStatus.COMPLETED)
                .build();

        VoiceInput saved = voiceInputRepository.save(voiceInput);
        LocalDateTime referenceTime = saved.getCreatedAt() == null
                ? LocalDateTime.now()
                : saved.getCreatedAt();
        List<ParentingRecordResponse> parentingRecords = parentingRecordService.analyzeAndSave(
                saved.getBabyId(),
                saved.getFamilyMemberId(),
                saved.getId(),
                saved.getTranscript(),
                referenceTime
        );

        return new SavedVoiceRecord(saved, parentingRecords);
    }

    private String transcribeAudio(MultipartFile audio) {
        AudioFileSupport.validate(audio);
        SttResponse response = sttClient.transcribe(audio);
        String transcript = response == null ? null : response.text();
        if (!StringUtils.hasText(transcript)) {
            throw new SttFailedException("음성을 텍스트로 변환하지 못했습니다.");
        }
        return transcript.trim();
    }

    private ParsedVoiceItemResponse toItem(ParentingRecordDraft draft) {
        return switch (draft.recordType()) {
            case FEEDING -> new ParsedVoiceItemResponse("feeding", feedingLabel(draft));
            case SLEEP -> new ParsedVoiceItemResponse("sleep", sleepLabel(draft));
            case DIAPER -> new ParsedVoiceItemResponse("diaper", diaperLabel(draft));
            case OTHER -> new ParsedVoiceItemResponse("other", draft.memo());
        };
    }

    private String feedingLabel(ParentingRecordDraft draft) {
        FeedingType feedingType = draft.feedingType();
        String label = feedingType == null ? "수유" : switch (feedingType) {
            case FORMULA -> "분유";
            case BREAST_MILK -> "모유";
            case BABY_FOOD -> "이유식";
            case OTHER -> "수유";
        };
        if (draft.amountMl() == null) {
            return "🍼 " + label;
        }
        return "🍼 " + label + " " + draft.amountMl() + "ml";
    }

    private String sleepLabel(ParentingRecordDraft draft) {
        if (draft.durationMinutes() == null) {
            return "😴 수면";
        }
        return "😴 수면 " + durationLabel(draft.durationMinutes());
    }

    private String diaperLabel(ParentingRecordDraft draft) {
        DiaperType diaperType = draft.diaperType();
        String label = diaperType == null ? "기저귀" : switch (diaperType) {
            case URINE -> "기저귀 (소변)";
            case STOOL -> "기저귀 (대변)";
            case MIXED -> "기저귀 (혼합)";
            case OTHER -> "기저귀";
        };
        return "💧 " + label;
    }

    private String durationLabel(int minutes) {
        int hours = minutes / 60;
        int remainingMinutes = minutes % 60;
        if (hours == 0) {
            return remainingMinutes + "분";
        }
        if (remainingMinutes == 0) {
            return hours + "시간";
        }
        return hours + "시간 " + remainingMinutes + "분";
    }

    private record SavedVoiceRecord(
            VoiceInput voiceInput,
            List<ParentingRecordResponse> parentingRecords
    ) {
    }
}
