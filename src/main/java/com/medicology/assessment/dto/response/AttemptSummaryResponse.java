package com.medicology.assessment.dto.response;

import com.medicology.assessment.entity.AttemptStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AttemptSummaryResponse(
        UUID attemptId,
        UUID contentId,
        AttemptStatus status,
        Instant startedAt,
        Instant submittedAt,
        BigDecimal score,
        BigDecimal maxScore,
        Boolean passed) {}
