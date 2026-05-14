package com.medicology.assessment.service.grading.model;

import java.math.BigDecimal;

public record AiEvaluationResponse(
        boolean correct,
        BigDecimal confidence,
        String explanation,
        Integer awardedPoints
) {
}
