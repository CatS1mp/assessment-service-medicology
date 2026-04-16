package com.medicology.assessment.service;

import com.medicology.assessment.config.AssessmentProperties;
import com.medicology.assessment.dto.request.LearningProgressSyncRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
@RequiredArgsConstructor
public class HttpLearningProgressGateway implements LearningProgressGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpLearningProgressGateway.class);

    private final AssessmentProperties assessmentProperties;
    private final RestClient.Builder restClientBuilder;

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
        String token = assessmentProperties.getLearningInternalToken();
        if (token == null || token.isBlank()) {
            log.warn("learning_sync_enabled_missing_token attemptId={}", request.attemptId());
            return;
        }
        String url = assessmentProperties.getLearningServiceBaseUrl() + "/api/v1/learning/internal/assessment-result";
        try {
            restClientBuilder.build()
                    .post()
                    .uri(url)
                    .header("X-Internal-Token", token)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.warn(
                    "learning_sync_http_failed status={} attemptId={} body={}",
                    e.getStatusCode(),
                    request.attemptId(),
                    e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn(
                    "learning_sync_transport_failed attemptId={} message={}",
                    request.attemptId(),
                    e.getMessage());
        }
    }
}
