package com.medicology.assessment.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ManualReviewItemResponse(
        UUID attemptAnswerId,
        UUID attemptId,
        UUID contentBlockId,
        String kindSnapshot,
        String promptSnapshot,
        String userAnswer,
        String payloadSnapshot,
        BigDecimal confidence,
        String explanation,
        Instant answeredAt) {}
