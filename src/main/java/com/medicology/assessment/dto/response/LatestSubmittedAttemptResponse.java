package com.medicology.assessment.dto.response;

import com.medicology.assessment.entity.AttemptStatus;
import java.time.Instant;
import java.util.UUID;

public record LatestSubmittedAttemptResponse(
        UUID attemptId, UUID contentId, AttemptStatus status, Instant submittedAt, Boolean passed) {}
