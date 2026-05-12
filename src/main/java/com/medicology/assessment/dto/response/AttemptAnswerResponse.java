package com.medicology.assessment.dto.response;

import com.medicology.assessment.entity.GradingStatus;
import java.time.Instant;
import java.util.UUID;

public record AttemptAnswerResponse(
        UUID attemptId,
        UUID contentBlockId,
        String userAnswer,
        GradingStatus gradingStatus,
        Instant answeredAt) {}
