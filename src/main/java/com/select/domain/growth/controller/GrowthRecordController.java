package com.select.domain.growth.controller;

import com.select.domain.growth.dto.request.GrowthRecordCreateRequest;
import com.select.domain.growth.dto.response.GrowthRecordCreateResponse;
import com.select.domain.growth.dto.response.GrowthRecordPage;
import java.time.LocalDate;
import com.select.domain.growth.service.GrowthService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/babies/{babyId}/growth-records", "/babies/{babyId}/growth_records"})
public class GrowthRecordController {

    private final GrowthService growthService;

    @Operation(summary = "성장 기록 조회", description = "측정일 범위와 ID 커서로 보호 중인 아기의 기록을 조회합니다.")
    @GetMapping
    public GrowthRecordPage listGrowth(
            @PathVariable Long babyId, @AuthenticationPrincipal(expression = "toString()") String userId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return growthService.listGrowth(babyId, Long.valueOf(userId), from, to, cursor, limit);
    }

    @Operation(summary = "성장 기록 저장", description = "키 또는 체중을 저장합니다. 동일 아기의 같은 날짜에는 한 건만 허용됩니다.")
    @PostMapping
    public ResponseEntity<GrowthRecordCreateResponse> createGrowth(
            @PathVariable Long babyId,
            @AuthenticationPrincipal(expression = "toString()") String userId,
            @Valid @RequestBody GrowthRecordCreateRequest request
    ) {
        GrowthRecordCreateResponse response =
                growthService.createGrowth(babyId, Long.valueOf(userId), request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
