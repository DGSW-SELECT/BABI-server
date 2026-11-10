package com.select.domain.growth.dto.response;

import java.util.List;

public record GrowthRecordPage(List<GrowthRecordItem> item, Long nextCursor) { }
