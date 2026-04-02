package com.medicology.assessment.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AttemptResultResponse(
        UUID attemptId,
        UUID assessmentId,
        BigDecimal score,
        BigDecimal maxScore,
        Integer correctAnswers,
        Integer totalQuestions,
        Boolean passed,
        Instant completedAt
) {
}
