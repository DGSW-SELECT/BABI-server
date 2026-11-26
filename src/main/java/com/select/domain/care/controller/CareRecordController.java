package com.select.domain.care.controller;

import com.select.domain.care.dto.request.CreateCareRecordRequest;
import com.select.domain.care.dto.request.CreateFeedingRequest;
import com.select.domain.care.dto.request.CreateTemperatureRequest;
import com.select.domain.care.dto.request.UpdateTemperatureRequest;
import com.select.domain.care.dto.response.CareRecordPage;
import com.select.domain.care.dto.response.CareRecordResponse;
import com.select.domain.care.entity.CareRecordType;
import com.select.domain.care.service.CareRecordService;
import com.select.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/babies/{babyId}")
public class CareRecordController {
    private final CareRecordService service;

    public CareRecordController(CareRecordService service) {
        this.service = service;
    }

    @PostMapping("/records")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CareRecordResponse> create(@PathVariable Long babyId,
            @AuthenticationPrincipal String userId, @Valid @RequestBody CreateCareRecordRequest request) {
        return new ApiResponse<>(true, "육아 기록을 등록했습니다.", service.create(babyId, Long.valueOf(userId), request));
    }

    @PostMapping("/feedings")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CareRecordResponse> createFeeding(@PathVariable Long babyId,
            @AuthenticationPrincipal String userId, @Valid @RequestBody CreateFeedingRequest request) {
        return new ApiResponse<>(true, "수유 기록을 등록했습니다.", service.create(babyId, Long.valueOf(userId),
                new CreateCareRecordRequest(CareRecordType.FEEDING, request.occurredAt(), null,
                        request.feedingType(), request.amountMl(), null, null, null, null, null, request.note())));
    }

    @Operation(summary = "체온 기록 저장")
    @PostMapping("/temperatures")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CareRecordResponse> createTemperature(@PathVariable Long babyId,
            @AuthenticationPrincipal String userId, @Valid @RequestBody CreateTemperatureRequest request) {
        return ApiResponse.success("체온 기록을 등록했습니다.", service.create(babyId, Long.valueOf(userId),
                new CreateCareRecordRequest(CareRecordType.TEMPERATURE, request.occurredAt(), null,
                        null, null, null, request.temperatureCelsius(), null, null, null, request.note())));
    }

    @Operation(summary = "날짜별 체온 기록 조회")
    @GetMapping("/temperatures")
    public ApiResponse<CareRecordPage> listTemperatures(@PathVariable Long babyId,
            @AuthenticationPrincipal String userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long authorMemberId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success("체온 기록을 조회했습니다.",
                service.list(babyId, Long.valueOf(userId), date, CareRecordType.TEMPERATURE, authorMemberId, page, size));
    }

    @Operation(summary = "체온 기록 수정")
    @PatchMapping("/temperatures/{recordId}")
    public ApiResponse<CareRecordResponse> updateTemperature(@PathVariable Long babyId, @PathVariable Long recordId,
            @AuthenticationPrincipal String userId, @Valid @RequestBody UpdateTemperatureRequest request) {
        return ApiResponse.success("체온 기록을 수정했습니다.",
                service.updateTemperature(babyId, Long.valueOf(userId), recordId, request));
    }

    @Operation(summary = "체온 기록 삭제")
    @DeleteMapping("/temperatures/{recordId}")
    public ApiResponse<Void> deleteTemperature(@PathVariable Long babyId, @PathVariable Long recordId,
            @AuthenticationPrincipal String userId) {
        service.deleteTemperature(babyId, Long.valueOf(userId), recordId);
        return ApiResponse.success("체온 기록을 삭제했습니다.");
    }

    @GetMapping("/records")
    public ApiResponse<CareRecordPage> list(@PathVariable Long babyId, @AuthenticationPrincipal String userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) CareRecordType recordType,
            @RequestParam(required = false) Long authorMemberId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return new ApiResponse<>(true, "육아 기록을 조회했습니다.",
                service.list(babyId, Long.valueOf(userId), date, recordType, authorMemberId, page, size));
    }
}
