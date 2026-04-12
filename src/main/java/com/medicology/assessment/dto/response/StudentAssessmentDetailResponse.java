package com.medicology.assessment.dto.response;

import com.medicology.assessment.entity.AssessmentStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StudentAssessmentDetailResponse(
        UUID id,
        String title,
        String description,
        UUID courseId,
        UUID sectionId,
        UUID lessonId,
        Integer passScore,
        Integer timeLimitMinutes,
        AssessmentStatus status,
        Boolean active,
        Instant createdAt,
        Instant updatedAt,
        List<StudentQuestionResponse> questions
) {
}
