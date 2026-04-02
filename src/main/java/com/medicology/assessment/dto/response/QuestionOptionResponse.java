package com.medicology.assessment.dto.response;

import java.util.UUID;

public record QuestionOptionResponse(
        UUID id,
        String content,
        Boolean correct,
        Integer displayOrder
) {
}
