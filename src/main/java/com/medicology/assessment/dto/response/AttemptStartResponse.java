package com.medicology.assessment.dto.response;

import com.medicology.assessment.entity.AttemptStatus;
import java.time.Instant;
import java.util.UUID;

public record AttemptStartResponse(
        UUID attemptId, UUID contentId, AttemptStatus status, Instant startedAt, Integer remainingSeconds) {}
