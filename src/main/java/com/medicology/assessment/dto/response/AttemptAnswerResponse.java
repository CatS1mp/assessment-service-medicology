package com.medicology.assessment.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AttemptAnswerResponse(
        UUID attemptId,
        UUID questionId,
        UUID selectedOptionId,
        Instant answeredAt
) {
}
