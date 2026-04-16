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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RuleGrader {

    private final ObjectMapper objectMapper;

    public GradingDecision grade(Question question, String userAnswer) {
        boolean correct = switch (question.getType()) {
            case ORDERING -> isOrderingCorrect(question, userAnswer);
            case MATCHING -> isMatchingCorrect(question, userAnswer);
            default -> {
                String normalizedUserAnswer = normalize(userAnswer);
                String normalizedExpected = normalize(readExpectedAnswer(question));
                yield normalizedExpected.equals(normalizedUserAnswer);
            }
        };
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

    private boolean isMatchingCorrect(Question question, String userAnswer) {
        Map<String, String> expected = parseMatchingMap(question.getAnswerKey());
        Map<String, String> actual = parseMatchingMap(userAnswer);
        if (expected.isEmpty() || actual.isEmpty() || expected.size() != actual.size()) {
            return false;
        }
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            String actualRight = actual.get(entry.getKey());
            if (actualRight == null || !actualRight.equals(entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private boolean isOrderingCorrect(Question question, String userAnswer) {
        List<String> expected = parseOrderingSequence(question.getAnswerKey());
        List<String> actual = parseOrderingSequence(userAnswer);
        if (expected.isEmpty() || actual.isEmpty()) {
            return false;
        }
        return expected.equals(actual);
    }

    private List<String> parseOrderingSequence(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            return parseOrderingNode(node);
        } catch (Exception ignored) {
            return parseOrderingCsv(raw);
        }
    }

    private List<String> parseOrderingNode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (node.isArray()) {
            return toNormalizedList(node);
        }
        if (node.isObject()) {
            // Accept common answer-key shapes for ORDERING.
            for (String key : List.of("correctOrder", "correct_order", "order", "sequence", "correct", "answer")) {
                JsonNode candidate = node.path(key);
                if (!candidate.isMissingNode() && !candidate.isNull()) {
                    if (candidate.isArray()) {
                        return toNormalizedList(candidate);
                    }
                    String text = candidate.asText("");
                    if (!text.isBlank()) {
                        return parseOrderingCsv(text);
                    }
                }
            }
            Iterator<JsonNode> elements = node.elements();
            if (elements.hasNext()) {
                JsonNode first = elements.next();
                if (first.isArray()) {
                    return toNormalizedList(first);
                }
            }
            return List.of();
        }
        if (node.isTextual()) {
            return parseOrderingCsv(node.asText(""));
        }
        return List.of(normalize(node.asText("")));
    }

    private List<String> toNormalizedList(JsonNode arrayNode) {
        List<String> values = new ArrayList<>();
        for (JsonNode item : arrayNode) {
            String value = normalize(item == null ? "" : item.asText(""));
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private List<String> parseOrderingCsv(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] parts = text.split(",");
        List<String> values = new ArrayList<>();
        for (String part : parts) {
            String value = normalize(part);
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private Map<String, String> parseMatchingMap(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            return parseMatchingNode(root);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Map<String, String> parseMatchingNode(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return Map.of();
        }
        if (root.isObject()) {
            for (String key : List.of("pairs", "correct", "answer")) {
                JsonNode candidate = root.path(key);
                if (!candidate.isMissingNode() && !candidate.isNull()) {
                    if (candidate.isArray()) {
                        return toMatchingMap(candidate);
                    }
                    if (candidate.isObject()) {
                        return toMatchingMapFromObject(candidate);
                    }
                }
            }
            return toMatchingMapFromObject(root);
        }
        if (root.isArray()) {
            return toMatchingMap(root);
        }
        return Map.of();
    }

    private Map<String, String> toMatchingMap(JsonNode arrayNode) {
        Map<String, String> result = new LinkedHashMap<>();
        for (JsonNode item : arrayNode) {
            if (item == null || item.isNull()) {
                continue;
            }
            String left = normalize(item.path("left").asText(""));
            String right = normalize(item.path("right").asText(""));
            if (!left.isBlank() && !right.isBlank()) {
                result.put(left, right);
            }
        }
        return result;
    }

    private Map<String, String> toMatchingMapFromObject(JsonNode objectNode) {
        Map<String, String> result = new LinkedHashMap<>();
        Iterator<String> fieldNames = objectNode.fieldNames();
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            String left = normalize(field);
            String right = normalize(objectNode.path(field).asText(""));
            if (!left.isBlank() && !right.isBlank()) {
                result.put(left, right);
            }
        }
        return result;
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
