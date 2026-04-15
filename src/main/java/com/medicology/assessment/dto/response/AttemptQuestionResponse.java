package com.medicology.assessment.dto.response;

import com.medicology.assessment.entity.QuestionType;
import java.util.UUID;

public record AttemptQuestionResponse(
        UUID id,
        String content,
        QuestionType type,
        Integer displayOrder,
        Integer points,
        String payload,
        Integer version
) {
}
