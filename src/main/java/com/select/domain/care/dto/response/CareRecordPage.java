package com.select.domain.care.dto.response;

import java.util.List;

public record CareRecordPage(List<CareRecordResponse> records, int page, int size, boolean hasNext) {
}
