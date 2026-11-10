package com.select.domain.growth.service;

import com.select.domain.growth.dto.request.GrowthRecordCreateRequest;
import com.select.domain.growth.dto.response.GrowthRecordCreateResponse;
import com.select.domain.growth.dto.response.GrowthRecordPage;
import java.time.LocalDate;

public interface GrowthService {
    GrowthRecordPage listGrowth(Long babyId, Long userId,
            LocalDate from, LocalDate to, Long cursor, int limit);

    GrowthRecordCreateResponse createGrowth(
            Long babyId,
            Long userId,
            GrowthRecordCreateRequest request
    );
}
