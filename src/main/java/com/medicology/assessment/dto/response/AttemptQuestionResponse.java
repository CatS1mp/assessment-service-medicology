package com.medicology.assessment.dto.response;

import com.medicology.assessment.entity.QuestionType;
import java.util.List;
import java.util.UUID;

public record AttemptQuestionResponse(
        UUID id,
        String content,
        String explanation,
        QuestionType type,
        Integer displayOrder,
        Integer points,
        List<AttemptQuestionOptionResponse> options
) {
}
