package com.medicology.assessment.service.grading;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medicology.assessment.entity.GradingSource;
import com.medicology.assessment.entity.GradingStatus;
import com.medicology.assessment.entity.Question;
import com.medicology.assessment.entity.QuestionType;
import com.medicology.assessment.service.grading.model.GradingDecision;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RuleGrader {

    private final ObjectMapper objectMapper;

    public GradingDecision grade(Question question, String userAnswer) {
        String normalizedUserAnswer = normalize(userAnswer);
        String normalizedExpected = normalize(readExpectedAnswer(question));
        boolean correct = normalizedExpected.equals(normalizedUserAnswer);
        return new GradingDecision(
                correct,
                correct ? BigDecimal.valueOf(question.getPoints()) : BigDecimal.ZERO,
                GradingStatus.FINALIZED,
                GradingSource.RULE,
                BigDecimal.ONE,
                correct ? "Rule grader matched answer key." : "Rule grader did not match answer key.",
                null,
                Instant.now());
    }

    private String readExpectedAnswer(Question question) {
        try {
            JsonNode answerKey = objectMapper.readTree(question.getAnswerKey());
            if (question.getType() == QuestionType.SHORT_ANSWER) {
                return answerKey.path("reference").asText("");
            }
            if (answerKey.isTextual()) {
                return answerKey.asText();
            }
            if (answerKey.has("correct")) {
                return answerKey.path("correct").asText("");
            }
            return answerKey.toString();
        } catch (Exception ex) {
            return question.getAnswerKey();
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
