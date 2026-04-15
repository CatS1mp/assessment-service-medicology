package com.medicology.assessment.dto.request;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LearningProgressSyncRequest(
        UUID userId,
        UUID courseId,
        UUID sectionId,
        UUID assessmentId,
        UUID attemptId,
        BigDecimal score,
        Boolean passed,
        String resultStatus,
        Instant completedAt
) {
}
