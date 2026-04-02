package com.medicology.assessment.service;

import com.medicology.assessment.config.AssessmentProperties;
import com.medicology.assessment.dto.request.LearningProgressSyncRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoggingLearningProgressGateway implements LearningProgressGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingLearningProgressGateway.class);

    private final AssessmentProperties assessmentProperties;

    @Override
    public void publishAssessmentResult(LearningProgressSyncRequest request) {
        if (!assessmentProperties.isLearningSyncEnabled()) {
            log.info(
                    "learning_sync_skipped contractPath={} attemptId={} userId={}",
                    assessmentProperties.getLearningContractPath(),
                    request.attemptId(),
                    request.userId());
            return;
        }

        log.warn(
                "learning_sync_enabled_but_not_implemented contractPath={} attemptId={} userId={} score={} passed={}",
                assessmentProperties.getLearningContractPath(),
                request.attemptId(),
                request.userId(),
                request.score(),
                request.passed());
    }
}
