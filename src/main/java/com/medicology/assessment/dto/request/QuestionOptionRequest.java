package com.medicology.assessment.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record QuestionOptionRequest(
        @NotBlank(message = "option content is required")
        String content,
        @NotNull(message = "correct flag is required")
        Boolean correct,
        @NotNull(message = "displayOrder is required")
        @Min(value = 0, message = "displayOrder must be non-negative")
        Integer displayOrder
) {
}
