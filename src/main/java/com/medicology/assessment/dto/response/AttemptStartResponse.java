package com.medicology.assessment.dto.response;

import com.medicology.assessment.entity.AttemptStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AttemptStartResponse(
        UUID attemptId,
        UUID assessmentId,
        String assessmentTitle,
        AttemptStatus status,
        Instant startedAt,
        List<AttemptQuestionResponse> questions
) {
}
