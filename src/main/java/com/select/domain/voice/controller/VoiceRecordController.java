package com.select.domain.voice.controller;

import com.select.domain.voice.dto.request.VoiceRecordRequest;
import com.select.domain.voice.dto.response.SttTranscriptResponse;
import com.select.domain.voice.dto.response.VoiceInputResponse;
import com.select.domain.voice.service.VoiceRecordService;
import com.select.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/babies/{babyId}/voice-records")
public class VoiceRecordController {

    private final VoiceRecordService voiceRecordService;

    public VoiceRecordController(VoiceRecordService voiceRecordService) {
        this.voiceRecordService = voiceRecordService;
    }

    @PostMapping(value = "/stt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SttTranscriptResponse> transcribe(
            @PathVariable Long babyId,
            @RequestPart("audio") MultipartFile audio
    ) {
        SttTranscriptResponse response = voiceRecordService.transcribe(babyId, audio);
        return new ApiResponse<>(true, "음성 변환이 완료되었습니다.", response);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<VoiceInputResponse> save(
            @PathVariable Long babyId,
            @Valid @RequestBody VoiceRecordRequest request
    ) {
        VoiceInputResponse response = voiceRecordService.save(babyId, request);
        return new ApiResponse<>(true, "음성 기록이 저장되었습니다.", response);
    }

    @GetMapping("/{voiceInputId}")
    public ApiResponse<VoiceInputResponse> get(
            @PathVariable Long babyId,
            @PathVariable Long voiceInputId
    ) {
        VoiceInputResponse response = voiceRecordService.get(babyId, voiceInputId);
        return new ApiResponse<>(true, "음성 기록을 조회했습니다.", response);
    }
}
