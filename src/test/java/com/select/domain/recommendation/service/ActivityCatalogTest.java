package com.select.domain.recommendation.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActivityCatalogTest {

    @Test
    void 아기_개월수에_맞는_활동만_반환한다() {
        ActivityCatalog catalog = new ActivityCatalog();

        List<ActivityCatalog.ActivitySuggestion> result =
                catalog.findByAgeMonths(3);

        assertEquals(1, result.size());
        assertEquals("부드러운 촉감 놀이", result.getFirst().title());
    }
}
