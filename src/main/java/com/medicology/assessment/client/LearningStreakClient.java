package com.medicology.assessment.client;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class LearningStreakClient {

    private static final String TOKEN_HEADER = "X-Internal-Service-Token";

    private final RestTemplate restTemplate;
    private final String learningBaseUrl;
    private final String internalServiceToken;

    public LearningStreakClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${learning.service-url:http://localhost:8081}") String learningBaseUrl,
            @Value("${app.internal-service-token:}") String internalServiceToken) {
        this.restTemplate = restTemplateBuilder.build();
        this.learningBaseUrl = trimTrailingSlash(learningBaseUrl);
        this.internalServiceToken = internalServiceToken;
    }

    public void pingStreak(UUID userId) {
        if (internalServiceToken == null || internalServiceToken.isBlank()) {
            return;
        }
        String url = learningBaseUrl + "/api/v1/learning/internal/users/" + userId + "/streak/ping";
        HttpHeaders headers = new HttpHeaders();
        headers.set(TOKEN_HEADER, internalServiceToken);
        try {
            restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(headers), Void.class);
        } catch (RestClientException ex) {
            log.warn("Failed to ping learning streak for user {}: {}", userId, ex.getMessage());
        }
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8081";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
