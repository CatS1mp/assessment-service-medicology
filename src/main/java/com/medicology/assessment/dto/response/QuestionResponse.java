package com.medicology.assessment.dto.response;

import com.medicology.assessment.entity.QuestionType;
import java.util.UUID;

public record QuestionResponse(
        UUID id,
        String content,
        String explanation,
        QuestionType type,
        Integer displayOrder,
        Integer points,
        Boolean active,
        String payload,
        String answerKey,
        Integer version
) {
}
