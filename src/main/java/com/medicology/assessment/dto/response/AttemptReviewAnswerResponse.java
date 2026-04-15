package com.medicology.assessment.dto.response;

import com.medicology.assessment.entity.GradingSource;
import com.medicology.assessment.entity.GradingStatus;
import com.medicology.assessment.entity.QuestionType;
import java.math.BigDecimal;
import java.util.UUID;

public record AttemptReviewAnswerResponse(
        UUID questionId,
        String questionContent,
        QuestionType questionType,
        Integer displayOrder,
        Integer points,
        String payload,
        String userAnswer,
        Boolean correct,
        BigDecimal awardedPoints,
        GradingStatus gradingStatus,
        GradingSource gradingSource,
        BigDecimal confidence,
        String explanation,
        String aiModel
) {
}
