package com.select.domain.growth.service;

import com.select.domain.growth.dto.request.GrowthRecordCreateRequest;
import com.select.domain.growth.dto.response.GrowthRecordCreateResponse;
import com.select.domain.growth.dto.response.GrowthRecordPage;
import com.select.domain.growth.dto.response.GrowthComparisonResponse;
import java.time.LocalDate;

public interface GrowthService {
    GrowthComparisonResponse compareGrowth(Long babyId, Long userId, Long growthRecordId);
    GrowthRecordPage listGrowth(Long babyId, Long userId,
            LocalDate from, LocalDate to, Long cursor, int limit);

    GrowthRecordCreateResponse createGrowth(
            Long babyId,
            Long userId,
            GrowthRecordCreateRequest request
    );
}
