package com.select.domain.recommendation.service;

import java.time.LocalDate;
import java.util.List;

public class ActivityRecommendationService {

    private final ActivityRecommender recommender;

    public ActivityRecommendationService(
            BabyAgeCalculator babyAgeCalculator,
            ActivityCatalog activityCatalog
    ) {
        this.recommender = input ->
                activityCatalog.findByAgeMonths(input.ageMonths());
        this.babyAgeCalculator = babyAgeCalculator;
    }

    public ActivityRecommendationService(ActivityRecommender recommender) {
        this.recommender = recommender;
        this.babyAgeCalculator = new BabyAgeCalculator();
    }

    private final BabyAgeCalculator babyAgeCalculator;

    public List<ActivityCatalog.ActivitySuggestion> recommend(
            LocalDate birthDate,
            LocalDate baseDate
    ) {
        long ageMonths = babyAgeCalculator.calculateMonths(birthDate, baseDate);
        return recommend(new ActivityRecommendationInput(ageMonths, "", ""));
    }

    public List<ActivityCatalog.ActivitySuggestion> recommend(
            ActivityRecommendationInput input
    ) {
        return recommender.recommend(input);
    }
}