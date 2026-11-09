package com.select.domain.growth.service;

import com.select.domain.growth.dto.request.GrowthRecordCreateRequest;
import com.select.domain.growth.dto.response.GrowthRecordCreateResponse;

public interface GrowthService {
    GrowthRecordCreateResponse createGrowth(
            Long babyId,
            Long userId,
            GrowthRecordCreateRequest request
    );
}
