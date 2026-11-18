package com.select.domain.voice.service;

import com.select.domain.baby.exception.BabyNotFoundException;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.baby.service.AccountContextService;
import com.select.domain.care.analyzer.CareRecordAnalyzer;
import com.select.domain.care.analyzer.CareRecordDraft;
import com.select.domain.care.dto.response.CareRecordResponse;
import com.select.domain.care.entity.DiaperType;
import com.select.domain.care.entity.FeedingType;
import com.select.domain.care.exception.CareRecordException;
import com.select.domain.care.service.CareRecordWriter;
import com.select.domain.voice.client.SttClient;
import com.select.domain.voice.client.dto.response.SttResponse;
import com.select.domain.voice.dto.request.ParsedVoiceRecordRequest;
import com.select.domain.voice.dto.request.VoiceRecordRequest;
import com.select.domain.voice.dto.request.VoiceRecordUpdateRequest;
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
import com.select.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class VoiceRecordService {

    private final VoiceInputRepository voiceInputRepository;
    private final CareRecordWriter careRecordWriter;
    private final CareRecordAnalyzer careRecordAnalyzer;
    private final SttClient sttClient;
    private final SttRequestRateLimiter sttRequestRateLimiter;
    private final BabyRepository babyRepository;
    private final AccountContextService accountContextService;
    private final TransactionTemplate transactions;

    public VoiceRecordService(
            VoiceInputRepository voiceInputRepository,
            CareRecordWriter careRecordWriter,
            CareRecordAnalyzer careRecordAnalyzer,
            SttClient sttClient,
            SttRequestRateLimiter sttRequestRateLimiter,
            BabyRepository babyRepository,
            AccountContextService accountContextService,
            PlatformTransactionManager transactionManager
    ) {
        this.voiceInputRepository = voiceInputRepository;
        this.careRecordWriter = careRecordWriter;
        this.careRecordAnalyzer = careRecordAnalyzer;
        this.sttClient = sttClient;
        this.sttRequestRateLimiter = sttRequestRateLimiter;
        this.babyRepository = babyRepository;
        this.accountContextService = accountContextService;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Transactional(propagation = Propagation.NEVER)
    public SttTranscriptResponse transcribe(Long babyId, Long userId, MultipartFile audio) {
        accountContextService.resolveForWrite(userId, babyId);
        return new SttTranscriptResponse(transcribeAudio(userId, audio));
    }

    /**
     * 프론트 음성 기록 화면용 STT 미리보기. DB에는 저장하지 않는다.
     */
    @Transactional(propagation = Propagation.NEVER)
    public ParsedVoiceRecordResponse parse(Long userId, Long babyId, MultipartFile audio) {
        accountContextService.resolveForWrite(userId, babyId);
        String transcript = transcribeAudio(userId, audio);
        LocalDateTime parsedAt = LocalDateTime.now();
        List<ParsedVoiceItemResponse> items = careRecordAnalyzer.analyze(transcript, parsedAt)
                .stream()
                .map(this::toItem)
                .toList();
        return new ParsedVoiceRecordResponse(transcript, items, parsedAt);
    }

    @Transactional(propagation = Propagation.NEVER)
    public VoiceInputResponse save(Long babyId, Long userId, VoiceRecordRequest request) {
        AccountContextService.AccountContext prechecked =
                accountContextService.resolveForWrite(userId, babyId);
        return transactions.execute(status -> {
            AccountContextService.AccountContext context = lockAndRecheck(
                    userId, prechecked.babyId()
            );
            VoiceInput voiceInput = voiceInputRepository.save(buildVoiceInput(
                    context, request.transcript()
            ));
            return VoiceInputResponse.from(voiceInput);
        });
    }

    /**
     * 사용자가 미리보기에서 확인한 transcript를 음성 기록과 육아기록으로 저장한다.
     *
     * <p>{@code parsedAt}이 있으면 미리보기를 만든 시각을 기준으로 다시 분석해
     * 화면에 보였던 상대 시간과 저장된 기록의 시간이 어긋나지 않게 한다.</p>
     */
    @Transactional(propagation = Propagation.NEVER)
    public VoiceRecordSavedResponse saveParsed(
            Long userId,
            Long babyId,
            ParsedVoiceRecordRequest request
    ) {
        AccountContextService.AccountContext prechecked =
                accountContextService.resolveForWrite(userId, babyId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime referenceTime = request.parsedAt() == null ? now : request.parsedAt();
        if (referenceTime.isBefore(now.minusHours(24)) || referenceTime.isAfter(now.plusHours(24))) {
            throw new CareRecordException(ErrorCode.INVALID_CARE_RECORD);
        }
        return transactions.execute(status -> {
            AccountContextService.AccountContext context = lockAndRecheck(
                    userId, prechecked.babyId()
            );
            VoiceInput voiceInput = voiceInputRepository.save(buildVoiceInput(
                    context, request.transcript()
            ));
            List<CareRecordResponse> careRecords = saveCareRecords(
                    context, voiceInput, referenceTime
            );
            return new VoiceRecordSavedResponse(true, voiceInput.getId(), careRecords);
        });
    }

    @Transactional(readOnly = true)
    public VoiceInputResponse get(Long babyId, Long userId, Long voiceInputId) {
        AccountContextService.AccountContext context =
                accountContextService.resolveForRead(userId, babyId);
        return voiceInputRepository.findByIdAndBabyId(voiceInputId, context.babyId())
                .map(VoiceInputResponse::from)
                .orElseThrow(VoiceInputNotFoundException::new);
    }

    @Transactional(propagation = Propagation.NEVER)
    public VoiceInputResponse update(
            Long babyId,
            Long userId,
            Long voiceInputId,
            VoiceRecordUpdateRequest request
    ) {
        AccountContextService.AccountContext prechecked =
                accountContextService.resolveForWrite(userId, babyId);
        return transactions.execute(status -> {
            AccountContextService.AccountContext context = lockAndRecheck(
                    userId, prechecked.babyId()
            );
            VoiceInput voiceInput = voiceInputRepository
                    .findByIdAndBabyId(voiceInputId, context.babyId())
                    .orElseThrow(VoiceInputNotFoundException::new);
            voiceInput.updateTranscript(request.transcript().trim());
            return VoiceInputResponse.from(voiceInputRepository.saveAndFlush(voiceInput));
        });
    }

    private AccountContextService.AccountContext lockAndRecheck(Long userId, Long babyId) {
        babyRepository.findByIdForUpdate(babyId)
                .orElseThrow(BabyNotFoundException::new);
        return accountContextService.resolveForWrite(userId, babyId);
    }

    private VoiceInput buildVoiceInput(
            AccountContextService.AccountContext context,
            String transcript
    ) {
        return VoiceInput.builder()
                .babyId(context.babyId())
                .familyMemberId(context.memberId())
                .audioUrl(null)
                .transcript(transcript.trim())
                .sttStatus(SttStatus.COMPLETED)
                .build();
    }

    private List<CareRecordResponse> saveCareRecords(
            AccountContextService.AccountContext context,
            VoiceInput voiceInput,
            LocalDateTime referenceTime
    ) {
        return careRecordWriter.analyzeAndSave(
                context.babyId(),
                context.memberId(),
                voiceInput.getId(),
                voiceInput.getTranscript(),
                referenceTime
        );
    }

    private String transcribeAudio(Long userId, MultipartFile audio) {
        AudioFileSupport.validate(audio);
        sttRequestRateLimiter.consume(userId);
        SttResponse response = sttClient.transcribe(audio);
        String transcript = response == null ? null : response.text();
        if (!StringUtils.hasText(transcript)) {
            throw new SttFailedException("음성을 텍스트로 변환하지 못했습니다.");
        }
        return transcript.trim();
    }

    private ParsedVoiceItemResponse toItem(CareRecordDraft draft) {
        return switch (draft.recordType()) {
            case FEEDING -> new ParsedVoiceItemResponse("feeding", feedingLabel(draft));
            case SLEEP -> new ParsedVoiceItemResponse("sleep", sleepLabel(draft));
            case DIAPER -> new ParsedVoiceItemResponse("diaper", diaperLabel(draft));
            default -> new ParsedVoiceItemResponse("other", draft.memo());
        };
    }

    private String feedingLabel(CareRecordDraft draft) {
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

    private String sleepLabel(CareRecordDraft draft) {
        if (draft.durationMinutes() == null) {
            return "😴 수면";
        }
        return "😴 수면 " + durationLabel(draft.durationMinutes());
    }

    private String diaperLabel(CareRecordDraft draft) {
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
}
