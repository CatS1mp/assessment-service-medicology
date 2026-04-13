package com.medicology.assessment.service;

import com.medicology.assessment.config.AssessmentProperties;
import com.medicology.assessment.exception.ConflictException;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class RestLearningEnrollmentClient implements LearningEnrollmentClient {

    private static final Logger log = LoggerFactory.getLogger(RestLearningEnrollmentClient.class);

    private final RestClient.Builder restClientBuilder;
    private final AssessmentProperties assessmentProperties;

    @Override
    public void assertCanAccessAssessment(UUID userId, UUID sectionId, UUID lessonId) {
        if (!assessmentProperties.isEnrollmentCheckEnabled()) {
            return;
        }
        String token = assessmentProperties.getLearningInternalToken();
        if (token == null || token.isBlank()) {
            log.warn("enrollment_check_enabled_missing_token");
            throw new ConflictException(1409, "Assessment access check is not configured.");
        }
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(
                        assessmentProperties.getLearningServiceBaseUrl() + "/api/v1/learning/internal/assessment-access")
                .queryParam("userId", userId)
                .queryParam("sectionId", sectionId);
        if (lessonId != null) {
            uriBuilder.queryParam("lessonId", lessonId);
        }
        URI uri = uriBuilder.build(true).toUri();
        try {
            restClientBuilder.build()
                    .get()
                    .uri(uri)
                    .header("X-Internal-Token", token)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new ConflictException(1409, "Enrollment required for this assessment.");
            }
            log.warn("enrollment_check_failed status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ConflictException(1409, "Unable to verify course enrollment.");
        }
    }
}
