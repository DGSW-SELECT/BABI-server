package com.select.domain.recommendation.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ActivityRecommendationServiceTest {

    @Test
    void sixMonthOldReceivesAgeAppropriateActivity() {
        ActivityRecommendationService service = new ActivityRecommendationService(
                new BabyAgeCalculator(), new ActivityCatalog());

        List<ActivityCatalog.ActivitySuggestion> result = service.recommend(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 7, 1));

        assertEquals(1, result.size());
        assertEquals("부드러운 촉감 놀이", result.getFirst().title());
    }
    @Test
    void 연령과_기록과_메모를_추천_담당자에게_전달한다() {
        ActivityRecommendationInput input = new ActivityRecommendationInput(
                6,
                "오늘 낮잠을 잤어요",
                "오후에는 조용한 놀이를 해주세요"
        );

        ActivityRecommendationInput[] received = new ActivityRecommendationInput[1];

        ActivityRecommender recommender = request -> {
            received[0] = request;
            return List.of();
        };

        ActivityRecommendationService service =
                new ActivityRecommendationService(recommender);

        service.recommend(input);

        assertEquals(input, received[0]);
    }
}
