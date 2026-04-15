package com.medicology.assessment.service.grading.model;

import com.medicology.assessment.entity.GradingSource;
import com.medicology.assessment.entity.GradingStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record GradingDecision(
        boolean correct,
        BigDecimal awardedPoints,
        GradingStatus gradingStatus,
        GradingSource gradingSource,
        BigDecimal confidence,
        String explanation,
        String aiModel,
        Instant evaluatedAt
) {
    public static GradingDecision manualReview(String explanation) {
        return new GradingDecision(
                false,
                BigDecimal.ZERO,
                GradingStatus.MANUAL_REVIEW,
                null,
                null,
                explanation,
                null,
                Instant.now());
    }
}
