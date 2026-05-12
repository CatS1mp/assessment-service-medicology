package com.medicology.assessment.service.grading.model;

import java.util.UUID;

public record ContentBlockSnapshot(
        UUID contentBlockId,
        UUID contentId,
        String kind,
        String payload,
        Integer maxScore,
        Integer orderIndex,
        Boolean isGradable
) {
    public int resolvedMaxPoints() {
        return maxScore == null || maxScore < 1 ? 1 : maxScore;
    }
}
