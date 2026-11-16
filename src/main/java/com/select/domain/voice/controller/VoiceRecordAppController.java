package com.select.domain.voice.controller;

import com.select.domain.voice.dto.request.ParsedVoiceRecordRequest;
import com.select.domain.voice.dto.response.ParsedVoiceRecordResponse;
import com.select.domain.voice.dto.response.VoiceRecordSavedResponse;
import com.select.domain.voice.service.VoiceRecordService;
import com.select.global.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 프론트 음성 기록 화면이 사용하는 간단한 API.
 *
 * <p>화면은 STT 미리보기와 확인 저장만 필요하므로
 * {@code /api/voice-records/parse}, {@code /api/voice-records} 두 경로만 노출한다.
 * 응답은 프론트의 기대 형식에 맞춰 {@code ApiResponse}로 감싸지 않는다.</p>
 */
@RestController
@RequestMapping("/api/voice-records")
public class VoiceRecordAppController {

    private final VoiceRecordService voiceRecordService;

    public VoiceRecordAppController(VoiceRecordService voiceRecordService) {
        this.voiceRecordService = voiceRecordService;
    }

    /**
     * 음성을 텍스트로 변환해 미리보기만 반환한다. DB에는 저장하지 않는다.
     */
    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ParsedVoiceRecordResponse parse(
            @AuthenticationPrincipal String principal,
            @RequestParam(required = false) Long babyId,
            @RequestPart("audio") MultipartFile audio
    ) {
        return voiceRecordService.parse(CurrentUser.id(principal), babyId, audio);
    }

    /**
     * 사용자가 확인한 transcript를 저장한다.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VoiceRecordSavedResponse save(
            @AuthenticationPrincipal String principal,
            @RequestParam(required = false) Long babyId,
            @Valid @RequestBody ParsedVoiceRecordRequest request
    ) {
        return voiceRecordService.saveParsed(CurrentUser.id(principal), babyId, request);
    }
}
