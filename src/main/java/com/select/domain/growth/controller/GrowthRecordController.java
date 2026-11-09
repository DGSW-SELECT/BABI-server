package com.select.domain.growth.controller;

import com.select.domain.growth.dto.request.GrowthRecordCreateRequest;
import com.select.domain.growth.dto.response.GrowthRecordCreateResponse;
import com.select.domain.growth.service.GrowthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/babies/{babyId}/growth_records")
public class GrowthRecordController {

    private final GrowthService growthService;

    @PostMapping
    public ResponseEntity<GrowthRecordCreateResponse> createGrowth(
            @PathVariable Long babyId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GrowthRecordCreateRequest request
    ) {
        GrowthRecordCreateResponse response =
                growthService.createGrowth(babyId, userId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
