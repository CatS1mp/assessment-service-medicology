package com.medicology.assessment.service.grading;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medicology.assessment.config.AssessmentProperties;
import com.medicology.assessment.entity.GradingSource;
import com.medicology.assessment.entity.GradingStatus;
import com.medicology.assessment.service.grading.model.AiEvaluationResponse;
import com.medicology.assessment.service.grading.model.ContentBlockSnapshot;
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
import java.util.ArrayList;
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
    private static final BigDecimal AUTO_INCORRECT_MAX_CONFIDENCE = new BigDecimal("0.70");

    private final AssessmentProperties assessmentProperties;
    private final ObjectMapper objectMapper;

    public GradingDecision grade(ContentBlockSnapshot snapshot, String userAnswer) {
        log.info(
                "ai_grading_started blockId={} kind={} model={} answerLength={}",
                snapshot.contentBlockId(),
                snapshot.kind(),
                assessmentProperties.getAiModel(),
                userAnswer == null ? 0 : userAnswer.length());
        AiEvaluationResponse aiResponse = evaluateWithProvider(snapshot, userAnswer);
        BigDecimal confidenceThreshold = BigDecimal.valueOf(assessmentProperties.getAiConfidenceThreshold());
        BigDecimal confidence = aiResponse.confidence() == null ? BigDecimal.ZERO : aiResponse.confidence();
        int maxPoints = snapshot.resolvedMaxPoints();

        if (confidence.compareTo(AUTO_INCORRECT_MAX_CONFIDENCE) <= 0) {
            log.warn(
                    "ai_grading_auto_incorrect blockId={} confidence={} reason={}",
                    snapshot.contentBlockId(),
                    confidence,
                    aiResponse.explanation());
            return new GradingDecision(
                    false,
                    BigDecimal.ZERO,
                    GradingStatus.FINALIZED,
                    GradingSource.AI,
                    confidence,
                    "Độ tin cậy AI ≤ 0,70, tự đánh sai. " + aiResponse.explanation(),
                    assessmentProperties.getAiModel(),
                    Instant.now());
        }

        if (confidence.compareTo(confidenceThreshold) < 0) {
            log.warn(
                    "ai_grading_manual_review blockId={} confidence={} threshold={} reason={}",
                    snapshot.contentBlockId(),
                    confidence,
                    confidenceThreshold,
                    aiResponse.explanation());
            return GradingDecision.manualReview(
                    "Độ tin cậy AI trong khoảng chấm thủ công (0,70, " + confidenceThreshold + "). Cần duyệt.");
        }

        boolean correct = aiResponse.correct();
        int awardedPoints = resolveAwardedPoints(aiResponse.awardedPoints(), correct, maxPoints);
        log.info("ai_grading_finalized blockId={} confidence={} correct={}", snapshot.contentBlockId(), confidence, correct);
        return new GradingDecision(
                correct,
                BigDecimal.valueOf(awardedPoints),
                GradingStatus.FINALIZED,
                GradingSource.AI,
                confidence,
                aiResponse.explanation(),
                assessmentProperties.getAiModel(),
                Instant.now());
    }

    private AiEvaluationResponse evaluateWithProvider(ContentBlockSnapshot snapshot, String userAnswer) {
        String apiKey = assessmentProperties.getAiApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.error("ai_grading_provider_unavailable reason=missing_api_key provider={}", assessmentProperties.getAiProvider());
            return new AiEvaluationResponse(
                    false,
                    BigDecimal.ZERO,
                    "Chưa cấu hình khóa API AI.",
                    null);
        }

        try {
            String endpoint = resolveEndpoint(assessmentProperties.getAiModel());
            String prompt = buildPrompt(snapshot, userAnswer);

            List<Map<String, Object>> tools = new ArrayList<>();
            if (assessmentProperties.isAiGroundingEnabled()) {
                tools.add(Map.of("google_search", Map.of()));
            }
            Map<String, Object> requestPayload = new java.util.LinkedHashMap<>();
            requestPayload.put("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
            requestPayload.put("generationConfig", Map.of(
                    "temperature", 0.1,
                    "responseMimeType", "application/json"));
            if (!tools.isEmpty()) {
                requestPayload.put("tools", tools);
            }
            String requestBody = objectMapper.writeValueAsString(requestPayload);

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
                        "ai_grading_provider_error blockId={} status={} body={}",
                        snapshot.contentBlockId(),
                        response.statusCode(),
                        truncate(response.body(), 500));
                return new AiEvaluationResponse(
                        false,
                        BigDecimal.ZERO,
                        "Nhà cung cấp AI trả về lỗi: " + response.statusCode(),
                        null);
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
                log.error("ai_grading_provider_empty_response blockId={}", snapshot.contentBlockId());
                return new AiEvaluationResponse(false, BigDecimal.ZERO, "Nhà cung cấp AI trả về nội dung trống.", null);
            }

            JsonNode aiJson = objectMapper.readTree(aiJsonText);
            boolean correct = aiJson.path("correct").asBoolean(false);
            BigDecimal confidence = parseConfidence(aiJson.path("confidence"));
            String explanation = aiJson.path("explanation").asText("Không có giải thích từ AI.");
            Integer awardedPoints = parseAwardedPoints(aiJson.path("awardedPoints"));
            List<String> suggestions = parseSuggestions(aiJson.path("suggestedCorrectAnswers"));
            String reason = aiJson.path("reason").asText("").trim();
            if (!reason.isBlank()) {
                explanation = reason + ". " + explanation;
            }
            if (!correct && !suggestions.isEmpty()) {
                explanation = explanation + " Gợi ý đáp án: " + String.join("; ", suggestions) + ".";
            } else if (!correct) {
                String fallback = extractExpectedReference(snapshot.payload());
                if (!fallback.isBlank()) {
                    explanation = explanation + " Gợi ý đáp án: " + fallback + ".";
                }
            }
            explanation = sanitizeExplanation(explanation);
            log.debug(
                    "ai_grading_provider_success blockId={} confidence={} correct={}",
                    snapshot.contentBlockId(),
                    confidence,
                    correct);
            return new AiEvaluationResponse(correct, confidence, explanation, awardedPoints);
        } catch (Exception ex) {
            log.error("ai_grading_provider_exception blockId={} message={}", snapshot.contentBlockId(), ex.getMessage(), ex);
            return new AiEvaluationResponse(
                    false,
                    BigDecimal.ZERO,
                    "Chấm AI thất bại: " + ex.getMessage(),
                    null);
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

    private String buildPrompt(ContentBlockSnapshot snapshot, String userAnswer) {
        String answerReference = extractExpectedReference(snapshot.payload());
        String questionText = extractQuestionText(snapshot.payload());
        return """
        You are a practical short-answer medical grader.
        You must evaluate semantic correctness against BOTH:
        1) reference answer from instructor, and
        2) up-to-date trusted medical information from web search.
        Use web-grounded evidence to validate whether the reference answer is still medically correct.
        If the reference answer conflicts with high-confidence medical consensus, explain the conflict clearly.

        Prefer concept-level understanding over exact wording.
        Do not reward clinically incorrect statements.
        Never infer extra facts that are not present in learner answer.
        If the learner answer is too vague, contradictory, unsafe, or off-topic, mark incorrect.

        Return ONLY valid JSON (no markdown, no prose) with exact schema:
        {
        "correct": boolean,
        "confidence": number,
        "awardedPoints": integer,
        "reason": string,
        "explanation": string,
        "suggestedCorrectAnswers": string[],
        "gradingCriteria": string[]
        }

        Hard rules:
        - confidence must be in [0.0, 1.0]
        - awardedPoints must be an integer in [0, %d]
        - if fully correct: correct=true and awardedPoints=%d
        - if partially correct but missing key points: %s
        - if incorrect/off-topic/unsafe: correct=false and awardedPoints=0
        - confidence should reflect semantic certainty, not exact keyword overlap
        - NEVER mention confidence value in reason/explanation text
        - reason must explicitly state why learner answer is wrong or right in one sentence
        - if incorrect: explanation is mandatory and must clearly say what is missing/wrong
        - if incorrect: provide 2-3 concise suggestedCorrectAnswers (each <= 20 words)
        - if correct: suggestedCorrectAnswers must be []
        - explanation and reason must be concise, learner-friendly, in Vietnamese with full diacritics (tiếng Việt có dấu)
        - gradingCriteria must contain 3-6 short bullet-like criteria used in grading
        - prioritize patient safety and clinical accuracy when judging equivalence
        - ignore grammar/spelling if medical meaning remains correct
        - accept medically equivalent terminology and common abbreviations only when unambiguous
        - if answer includes both correct and clearly wrong clinical claims, mark incorrect
        - if web evidence and reference answer diverge, prefer safer and better-supported medical evidence
        - make grading easier/fairer: if learner answer captures core medical concept and has no dangerous error, it can be correct

        Question:
        %s

        Reference answer:
        %s

        Learner answer:
        %s
        """
                .formatted(
                        maxPointsForPrompt(snapshot),
                        maxPointsForPrompt(snapshot),
                        partialRuleForPrompt(snapshot),
                        questionText,
                        answerReference,
                        userAnswer == null ? "" : userAnswer);
    }

    private String extractQuestionText(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root.has("prompt")) {
                return root.path("prompt").asText("");
            }
            if (root.has("question")) {
                return root.path("question").asText("");
            }
            return root.toString();
        } catch (Exception ex) {
            return payload;
        }
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

    private Integer parseAwardedPoints(JsonNode awardedPointsNode) {
        if (awardedPointsNode == null || awardedPointsNode.isMissingNode() || awardedPointsNode.isNull()) {
            return null;
        }
        try {
            if (awardedPointsNode.isNumber()) {
                return awardedPointsNode.asInt();
            }
            String raw = awardedPointsNode.asText("").trim();
            if (raw.isBlank()) {
                return null;
            }
            return Integer.parseInt(raw);
        } catch (Exception ex) {
            return null;
        }
    }

    private List<String> parseSuggestions(JsonNode node) {
        if (node == null || node.isMissingNode() || !node.isArray()) {
            return List.of();
        }
        List<String> suggestions = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item == null ? "" : item.asText("").trim();
            if (!value.isBlank()) {
                suggestions.add(value);
            }
        }
        return suggestions;
    }

    private String sanitizeExplanation(String explanation) {
        if (explanation == null || explanation.isBlank()) {
            return "AI không cung cấp giải thích chi tiết.";
        }
        return explanation.replaceAll("(?i)confidence\\s*[:=]?\\s*\\d+(?:\\.\\d+)?%?", "").trim();
    }

    private int resolveAwardedPoints(Integer suggestedPoints, boolean correct, int maxPoints) {
        if (correct) {
            return maxPoints;
        }
        int fallback = 0;
        if (suggestedPoints == null) {
            return fallback;
        }
        int normalized = Math.max(0, Math.min(maxPoints, suggestedPoints));
        return normalized;
    }

    private int maxPointsForPrompt(ContentBlockSnapshot snapshot) {
        return Math.max(1, snapshot.resolvedMaxPoints());
    }

    private String partialRuleForPrompt(ContentBlockSnapshot snapshot) {
        int maxPoints = maxPointsForPrompt(snapshot);
        if (maxPoints <= 1) {
            return "use awardedPoints=0 unless fully correct.";
        }
        return "correct=false and awardedPoints in [1, " + (maxPoints - 1) + "]";
    }

    private String extractExpectedReference(String payload) {
        try {
            JsonNode answerKeyNode = objectMapper.readTree(payload);
            if (answerKeyNode.has("sampleAnswer")) {
                return answerKeyNode.path("sampleAnswer").asText("");
            }
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
            return payload;
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
