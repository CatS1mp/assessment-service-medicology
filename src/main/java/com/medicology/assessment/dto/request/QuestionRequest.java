package com.medicology.assessment.dto.request;

import com.medicology.assessment.entity.QuestionType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record QuestionRequest(
        @NotBlank(message = "question content is required")
        String content,
        String explanation,
        @NotNull(message = "question type is required")
        QuestionType type,
        @NotNull(message = "displayOrder is required")
        @Min(value = 0, message = "displayOrder must be non-negative")
        Integer displayOrder,
        @NotNull(message = "points is required")
        @Min(value = 1, message = "points must be at least 1")
        Integer points,
        Boolean active,
        @NotBlank(message = "payload is required")
        String payload,
        @NotBlank(message = "answerKey is required")
        String answerKey
) {
}
