package com.medicology.assessment.dto.request;

import com.medicology.assessment.entity.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

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
        @NotEmpty(message = "question must have at least one option")
        List<@Valid QuestionOptionRequest> options
) {
}
