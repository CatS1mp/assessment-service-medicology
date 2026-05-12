package com.medicology.assessment.dto.response;

import com.medicology.assessment.entity.AttemptStatus;
import com.medicology.assessment.entity.ResultStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AttemptResultResponse(
        UUID attemptId,
        UUID contentId,
        BigDecimal score,
        BigDecimal maxScore,
        Integer correctAnswers,
        Integer totalQuestions,
        Boolean passed,
        Instant completedAt,
        ResultStatus resultStatus,
        AttemptStatus attemptStatus,
        Integer pendingManualReviews) {}
