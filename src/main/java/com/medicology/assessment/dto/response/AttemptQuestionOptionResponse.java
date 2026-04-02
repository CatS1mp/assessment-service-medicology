package com.medicology.assessment.dto.response;

import java.util.UUID;

public record AttemptQuestionOptionResponse(
        UUID id,
        String content,
        Integer displayOrder
) {
}
