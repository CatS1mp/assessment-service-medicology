package com.medicology.assessment.dto.response;

import com.medicology.assessment.entity.QuestionType;
import java.util.List;
import java.util.UUID;

public record StudentQuestionResponse(
        UUID id,
        String content,
        QuestionType type,
        Integer displayOrder,
        Integer points,
        Boolean active,
        List<StudentQuestionOptionResponse> options
) {
}
