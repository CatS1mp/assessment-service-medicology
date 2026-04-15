package com.medicology.assessment.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ManualReviewItemResponse(
        UUID attemptAnswerId,
        UUID attemptId,
        UUID questionId,
        String questionContent,
        String userAnswer,
        String payloadSnapshot,
        String answerKeySnapshot,
        BigDecimal confidence,
        String explanation,
        Instant answeredAt
) {
}
