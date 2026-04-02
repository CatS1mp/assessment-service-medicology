package com.medicology.assessment.dto.response;

import com.medicology.assessment.entity.AssessmentStatus;
import java.time.Instant;
import java.util.UUID;

public record AssessmentSummaryResponse(
        UUID id,
        String title,
        UUID courseId,
        UUID sectionId,
        UUID lessonId,
        Integer passScore,
        Integer questionCount,
        AssessmentStatus status,
        Boolean active,
        Instant updatedAt
) {
}
