package com.medicology.assessment.dto.request;

import com.medicology.assessment.entity.AssessmentStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssessmentRequest(
        @NotBlank(message = "title is required")
        String title,
        String description,
        @NotNull(message = "courseId is required")
        UUID courseId,
        @NotNull(message = "sectionId is required")
        UUID sectionId,
        UUID lessonId,
        @NotNull(message = "passScore is required")
        @Min(value = 0, message = "passScore must be at least 0")
        @Max(value = 100, message = "passScore must be at most 100")
        Integer passScore,
        @Min(value = 1, message = "timeLimitMinutes must be at least 1")
        Integer timeLimitMinutes,
        @NotNull(message = "status is required")
        AssessmentStatus status,
        Boolean active
) {
}
