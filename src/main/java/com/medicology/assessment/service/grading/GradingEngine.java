package com.medicology.assessment.service.grading;

import com.medicology.assessment.service.grading.model.ContentBlockSnapshot;
import com.medicology.assessment.service.grading.model.GradingDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GradingEngine {

    private final RuleGrader ruleGrader;
    private final AiShortAnswerGrader aiShortAnswerGrader;

    public GradingDecision grade(ContentBlockSnapshot snapshot, String userAnswer) {
        if ("SHORT_ANSWER".equals(snapshot.kind())) {
            return aiShortAnswerGrader.grade(snapshot, userAnswer);
        }
        return ruleGrader.grade(snapshot, userAnswer);
    }
}
