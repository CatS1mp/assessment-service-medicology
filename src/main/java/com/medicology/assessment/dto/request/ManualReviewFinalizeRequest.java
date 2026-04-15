package com.medicology.assessment.dto.request;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ManualReviewFinalizeRequest(
        @NotNull(message = "correct is required")
        Boolean correct,
        String explanation,
        BigDecimal awardedPoints
) {
}
