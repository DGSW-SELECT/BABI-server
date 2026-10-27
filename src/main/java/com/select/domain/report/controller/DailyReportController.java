package com.select.domain.report.controller;

import com.select.domain.report.dto.request.CreateDailyReportRequest;
import com.select.domain.report.dto.request.UpdateDailyReportRequest;
import com.select.domain.report.dto.response.DailyReportResponse;
import com.select.domain.report.service.DailyReportService;
import com.select.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/babi/api/babies/{babyId}/daily-reports")
public class DailyReportController {

    private final DailyReportService dailyReportService;

    public DailyReportController(DailyReportService dailyReportService) {
        this.dailyReportService = dailyReportService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DailyReportResponse> create(
            @AuthenticationPrincipal String userId,
            @PathVariable Long babyId,
            @Valid @RequestBody CreateDailyReportRequest request
    ) {
        return new ApiResponse<>(
                true,
                "리포트를 생성했습니다.",
                dailyReportService.create(Long.valueOf(userId), babyId, request)
        );
    }

    @GetMapping("/{date}")
    public ApiResponse<DailyReportResponse> get(
            @AuthenticationPrincipal String userId,
            @PathVariable Long babyId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return new ApiResponse<>(true, "리포트를 조회했습니다.", dailyReportService.get(Long.valueOf(userId), babyId, date));
    }

    @PatchMapping("/{date}")
    public ApiResponse<DailyReportResponse> update(
            @AuthenticationPrincipal String userId,
            @PathVariable Long babyId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody UpdateDailyReportRequest request
    ) {
        return new ApiResponse<>(
                true,
                "리포트를 수정했습니다.",
                dailyReportService.update(Long.valueOf(userId), babyId, date, request)
        );
    }

    @DeleteMapping("/{date}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal String userId,
            @PathVariable Long babyId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        dailyReportService.delete(Long.valueOf(userId), babyId, date);
        return new ApiResponse<>(true, "리포트를 삭제했습니다.", null);
    }
}
