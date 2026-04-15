package com.medicology.assessment.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AttemptAnswerRequest(
        @NotNull(message = "questionId is required")
        UUID questionId,
        @NotNull(message = "userAnswer is required")
        String userAnswer
) {
}
