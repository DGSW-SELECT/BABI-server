package com.select.domain.handover.dto.response;

import java.time.LocalDate;

public record HandoverBriefingResponse(
        LocalDate briefingDate,
        String summary
) {
}
