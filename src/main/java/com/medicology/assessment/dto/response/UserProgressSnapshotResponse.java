package com.medicology.assessment.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserProgressSnapshotResponse(
        List<ContentOutcomeItem> latestFinalizedByContent,
        List<InProgressAttemptItem> inProgressAttempts,
        List<RecentGradedAttemptItem> recentGradedAttempts) {

    public record ContentOutcomeItem(UUID contentId, boolean passed, Instant completedAt) {}

    public record InProgressAttemptItem(UUID contentId, UUID attemptId) {}

    public record RecentGradedAttemptItem(Instant submittedAt, BigDecimal score, BigDecimal maxScore) {}
}
