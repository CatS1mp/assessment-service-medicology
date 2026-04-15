package com.medicology.assessment.service.grading;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medicology.assessment.config.AssessmentProperties;
import com.medicology.assessment.entity.GradingSource;
import com.medicology.assessment.entity.GradingStatus;
import com.medicology.assessment.entity.Question;
import com.medicology.assessment.service.grading.model.AiEvaluationResponse;
import com.medicology.assessment.service.grading.model.GradingDecision;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiShortAnswerGrader {

    private static final Logger log = LoggerFactory.getLogger(AiShortAnswerGrader.class);

    private final AssessmentProperties assessmentProperties;
    private final ObjectMapper objectMapper;

    public GradingDecision grade(Question question, String userAnswer) {
        log.info(
                "ai_grading_started questionId={} type={} model={} answerLength={}",
                question.getId(),
                question.getType(),
                assessmentProperties.getAiModel(),
                userAnswer == null ? 0 : userAnswer.length());
        AiEvaluationResponse aiResponse = evaluateWithProvider(question, userAnswer);
        BigDecimal confidenceThreshold = BigDecimal.valueOf(assessmentProperties.getAiConfidenceThreshold());
        if (aiResponse.confidence() == null || aiResponse.confidence().compareTo(confidenceThreshold) < 0) {
            log.warn(
                    "ai_grading_manual_review questionId={} confidence={} threshold={} reason={}",
                    question.getId(),
                    aiResponse.confidence(),
                    confidenceThreshold,
                    aiResponse.explanation());
            return GradingDecision.manualReview(
                    "AI confidence below threshold (" + confidenceThreshold + "). Review required.");
        }

        boolean correct = aiResponse.correct();
        log.info(
                "ai_grading_finalized questionId={} confidence={} correct={}",
                question.getId(),
                aiResponse.confidence(),
                correct);
        return new GradingDecision(
                correct,
                correct ? BigDecimal.valueOf(question.getPoints()) : BigDecimal.ZERO,
                GradingStatus.FINALIZED,
                GradingSource.AI,
                aiResponse.confidence(),
                aiResponse.explanation(),
                assessmentProperties.getAiModel(),
                Instant.now());
    }

    private AiEvaluationResponse evaluateWithProvider(Question question, String userAnswer) {
        String apiKey = assessmentProperties.getAiApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.error("ai_grading_provider_unavailable reason=missing_api_key provider={}", assessmentProperties.getAiProvider());
            return new AiEvaluationResponse(
                    false,
                    BigDecimal.ZERO,
                    "AI API key is not configured.");
        }

        try {
            String endpoint = resolveEndpoint(assessmentProperties.getAiModel());
            String prompt = buildPrompt(question, userAnswer);

            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "contents",
                    List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                    "generationConfig",
                    Map.of(
                            "temperature", 0.1,
                            "responseMimeType", "application/json")));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(appendApiKey(endpoint, apiKey)))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error(
                        "ai_grading_provider_error questionId={} status={} body={}",
                        question.getId(),
                        response.statusCode(),
                        truncate(response.body(), 500));
                return new AiEvaluationResponse(
                        false,
                        BigDecimal.ZERO,
                        "AI provider returned non-success status: " + response.statusCode());
            }

            JsonNode providerRoot = objectMapper.readTree(response.body());
            String aiJsonText = providerRoot.path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text")
                    .asText("");
            if (aiJsonText.isBlank()) {
                log.error("ai_grading_provider_empty_response questionId={}", question.getId());
                return new AiEvaluationResponse(false, BigDecimal.ZERO, "AI provider returned empty content.");
            }

            JsonNode aiJson = objectMapper.readTree(aiJsonText);
            boolean correct = aiJson.path("correct").asBoolean(false);
            BigDecimal confidence = parseConfidence(aiJson.path("confidence"));
            String explanation = aiJson.path("explanation").asText("AI explanation is unavailable.");
            log.debug(
                    "ai_grading_provider_success questionId={} confidence={} correct={}",
                    question.getId(),
                    confidence,
                    correct);
            return new AiEvaluationResponse(correct, confidence, explanation);
        } catch (Exception ex) {
            log.error("ai_grading_provider_exception questionId={} message={}", question.getId(), ex.getMessage(), ex);
            return new AiEvaluationResponse(
                    false,
                    BigDecimal.ZERO,
                    "AI evaluation failed: " + ex.getMessage());
        }
    }

    private String resolveEndpoint(String model) {
        String customEndpoint = assessmentProperties.getAiEndpoint();
        if (customEndpoint != null && !customEndpoint.isBlank()) {
            return customEndpoint.trim();
        }
        return "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";
    }

    private String appendApiKey(String endpoint, String apiKey) {
        String delimiter = endpoint.contains("?") ? "&" : "?";
        return endpoint + delimiter + "key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
    }

    private String buildPrompt(Question question, String userAnswer) {
        String answerReference = extractExpectedReference(question.getAnswerKey());
        return """
        You are grading a short-answer response for medical learning.
        Return ONLY JSON (no markdown) with schema:
        {
        "correct": boolean,
        "confidence": number,
        "explanation": string,
        "suggestedCorrectAnswers": string[]
        }

        Rules:
        - confidence range 0.0 to 1.0
        - if incorrect, include 2-3 suggestedCorrectAnswers
        - if correct, suggestedCorrectAnswers must be []
        - explanation must be concise and learner-friendly

        Question:
        %s

        Reference answer:
        %s

        Learner answer:
        %s
        """.formatted(question.getContent(), answerReference, userAnswer == null ? "" : userAnswer);
    }

    private BigDecimal parseConfidence(JsonNode confidenceNode) {
        if (confidenceNode == null || confidenceNode.isMissingNode() || confidenceNode.isNull()) {
            return BigDecimal.ZERO;
        }
        try {
            if (confidenceNode.isNumber()) {
                return BigDecimal.valueOf(confidenceNode.asDouble());
            }
            String raw = confidenceNode.asText("0").trim();
            if (raw.isBlank()) {
                return BigDecimal.ZERO;
            }
            return new BigDecimal(raw);
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
    }

    private String extractExpectedReference(String answerKey) {
        try {
            JsonNode answerKeyNode = objectMapper.readTree(answerKey);
            if (answerKeyNode.has("reference")) {
                return answerKeyNode.path("reference").asText("");
            }
            if (answerKeyNode.has("correct")) {
                return answerKeyNode.path("correct").asText("");
            }
            if (answerKeyNode.isTextual()) {
                return answerKeyNode.asText("");
            }
            return answerKeyNode.toString();
        } catch (Exception ex) {
            return answerKey;
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

}
