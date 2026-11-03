package com.select.domain.recommendation.service;

import java.util.List;

public class ActivityCatalog {

    private final List<ActivitySuggestion> suggestions = List.of(
            new ActivitySuggestion(
                    "부드러운 촉감 놀이",
                    "보호자가 다양한 천의 촉감을 짧게 느끼게 합니다.",
                    0,
                    6
            ),
            new ActivitySuggestion(
                    "소리 따라 하기",
                    "보호자가 간단한 소리를 내고 아기의 반응을 관찰합니다.",
                    7,
                    12
            )
    );

    public List<ActivitySuggestion> findByAgeMonths(long ageMonths) {
        return suggestions.stream()
                .filter(suggestion ->
                        suggestion.minAgeMonths() <= ageMonths
                                && ageMonths <= suggestion.maxAgeMonths()
                )
                .toList();
    }

    public record ActivitySuggestion(
            String title,
            String description,
            int minAgeMonths,
            int maxAgeMonths
    ) {
    }
}
