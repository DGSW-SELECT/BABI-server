package com.select.domain.voice.controller;

import com.select.domain.voice.dto.request.VoiceRecordRequest;
import com.select.domain.voice.dto.request.VoiceRecordUpdateRequest;
import com.select.domain.voice.dto.response.SttJobResponse;
import com.select.domain.voice.dto.response.SttTranscriptResponse;
import com.select.domain.voice.dto.response.VoiceInputResponse;
import com.select.domain.voice.service.SttJobService;
import com.select.domain.voice.service.VoiceRecordService;
import com.select.global.response.ApiResponse;
import com.select.global.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    private final SttJobService sttJobService;

    public VoiceRecordController(
            VoiceRecordService voiceRecordService,
            SttJobService sttJobService
    ) {
        this.voiceRecordService = voiceRecordService;
        this.sttJobService = sttJobService;
    }

    @PostMapping(value = "/stt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SttTranscriptResponse> transcribe(
            @PathVariable Long babyId,
            @AuthenticationPrincipal String principal,
            @RequestPart("audio") MultipartFile audio
    ) {
        SttTranscriptResponse response = voiceRecordService.transcribe(
                babyId, CurrentUser.id(principal), audio
        );
        return new ApiResponse<>(true, "음성 변환이 완료되었습니다.", response);
    }

    /**
     * 음성 파일을 저장하고 비동기 STT 작업을 등록한다. 결과는 작업 조회 API로 확인한다.
     */
    @PostMapping(value = "/stt-jobs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<SttJobResponse> submitSttJob(
            @PathVariable Long babyId,
            @AuthenticationPrincipal String principal,
            @RequestPart("audio") MultipartFile audio
    ) {
        SttJobResponse response = sttJobService.submit(
                babyId, CurrentUser.id(principal), audio
        );
        return new ApiResponse<>(true, "음성 변환 작업이 등록되었습니다.", response);
    }

    @GetMapping("/stt-jobs/{jobId}")
    public ApiResponse<SttJobResponse> getSttJob(
            @PathVariable Long babyId,
            @AuthenticationPrincipal String principal,
            @PathVariable Long jobId
    ) {
        SttJobResponse response = sttJobService.get(
                babyId, CurrentUser.id(principal), jobId
        );
        return new ApiResponse<>(true, "음성 변환 작업을 조회했습니다.", response);
    }

    @PostMapping("/stt-jobs/{jobId}/retry")
    public ApiResponse<SttJobResponse> retrySttJob(
            @PathVariable Long babyId,
            @AuthenticationPrincipal String principal,
            @PathVariable Long jobId
    ) {
        SttJobResponse response = sttJobService.retry(
                babyId, CurrentUser.id(principal), jobId
        );
        return new ApiResponse<>(true, "음성 변환 작업을 다시 등록했습니다.", response);
    }

    /**
     * 사용자가 미리보기에서 확인한 STT 결과를 저장한다.
     */
    @PostMapping("/stt-jobs/{jobId}/confirm")
    public ApiResponse<SttJobResponse> confirmSttJob(
            @PathVariable Long babyId,
            @AuthenticationPrincipal String principal,
            @PathVariable Long jobId
    ) {
        SttJobResponse response = sttJobService.confirm(
                babyId, CurrentUser.id(principal), jobId
        );
        return new ApiResponse<>(true, "음성 기록이 저장되었습니다.", response);
    }

    /**
     * 사용자가 녹음을 버릴 때 확인 전 작업과 음성 파일을 삭제한다.
     */
    @DeleteMapping("/stt-jobs/{jobId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelSttJob(
            @PathVariable Long babyId,
            @AuthenticationPrincipal String principal,
            @PathVariable Long jobId
    ) {
        sttJobService.cancel(babyId, CurrentUser.id(principal), jobId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<VoiceInputResponse> save(
            @PathVariable Long babyId,
            @AuthenticationPrincipal String principal,
            @Valid @RequestBody VoiceRecordRequest request
    ) {
        VoiceInputResponse response = voiceRecordService.save(
                babyId, CurrentUser.id(principal), request
        );
        return new ApiResponse<>(true, "음성 기록이 저장되었습니다.", response);
    }

    @GetMapping("/{voiceInputId}")
    public ApiResponse<VoiceInputResponse> get(
            @PathVariable Long babyId,
            @AuthenticationPrincipal String principal,
            @PathVariable Long voiceInputId
    ) {
        VoiceInputResponse response = voiceRecordService.get(
                babyId, CurrentUser.id(principal), voiceInputId
        );
        return new ApiResponse<>(true, "음성 기록을 조회했습니다.", response);
    }

    @PatchMapping("/{voiceInputId}")
    public ApiResponse<VoiceInputResponse> update(
            @PathVariable Long babyId,
            @AuthenticationPrincipal String principal,
            @PathVariable Long voiceInputId,
            @Valid @RequestBody VoiceRecordUpdateRequest request
    ) {
        VoiceInputResponse response = voiceRecordService.update(
                babyId, CurrentUser.id(principal), voiceInputId, request
        );
        return new ApiResponse<>(true, "음성 기록이 수정되었습니다.", response);
    }
}
