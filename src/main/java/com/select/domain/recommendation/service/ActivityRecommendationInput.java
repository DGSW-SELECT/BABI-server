package com.select.domain.recommendation.service;

public record ActivityRecommendationInput(
        long ageMonths,
        String dailyRecord,
        String handoverMemo
) {
}