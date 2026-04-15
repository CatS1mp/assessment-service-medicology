package com.medicology.assessment.service.grading;

import com.medicology.assessment.entity.Question;
import com.medicology.assessment.entity.QuestionType;
import com.medicology.assessment.service.grading.model.GradingDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GradingEngine {

    private final RuleGrader ruleGrader;
    private final AiShortAnswerGrader aiShortAnswerGrader;

    public GradingDecision grade(Question question, String userAnswer) {
        if (question.getType() == QuestionType.SHORT_ANSWER) {
            return aiShortAnswerGrader.grade(question, userAnswer);
        }
        return ruleGrader.grade(question, userAnswer);
    }
}
