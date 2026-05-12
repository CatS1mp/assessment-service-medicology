package com.medicology.assessment.dto.response;

import com.medicology.assessment.entity.GradingSource;
import com.medicology.assessment.entity.GradingStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record AttemptReviewAnswerResponse(
        UUID contentBlockId,
        String blockKind,
        Integer orderIndex,
        Integer maxScore,
        String payload,
        String userAnswer,
        Boolean correct,
        BigDecimal awardedPoints,
        GradingStatus gradingStatus,
        GradingSource gradingSource,
        BigDecimal confidence,
        String explanation,
        String aiModel) {}
