package com.medicology.assessment.dto.response;

import java.util.UUID;

public record StudentQuestionOptionResponse(
        UUID id,
        String content,
        Integer displayOrder
) {
}
