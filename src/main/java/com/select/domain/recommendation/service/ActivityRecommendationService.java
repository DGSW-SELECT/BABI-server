package com.select.domain.recommendation.service;
import java.time.LocalDate;
import java.util.List;

public class ActivityRecommendationService {
    private final BabyAgeCalculator babyAgeCalculator;
    private final ActivityCatalog activityCatalog;

    public ActivityRecommendationService(
            BabyAgeCalculator babyAgeCalculator,
            ActivityCatalog activityCatalog
            ) {
        this.babyAgeCalculator = babyAgeCalculator;
        this.activityCatalog = activityCatalog;
    }

    public List<ActivityCatalog.ActivitySuggestion> recommend(
            LocalDate birthDate,
            LocalDate baseDate
    ) {
        long ageMonths =
                babyAgeCalculator.calculateMonths(birthDate,baseDate);
        return activityCatalog.findByAgeMonths(ageMonths);
    }
}

