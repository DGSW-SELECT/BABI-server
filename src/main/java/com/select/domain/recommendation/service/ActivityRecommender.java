package com.select.domain.recommendation.service;

import java.util.List;

public interface ActivityRecommender {

    List<ActivityCatalog.ActivitySuggestion> recommend(
            ActivityRecommendationInput input
    );
}