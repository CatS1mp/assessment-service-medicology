package com.medicology.assessment.service.grading;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medicology.assessment.entity.GradingSource;
import com.medicology.assessment.entity.GradingStatus;
import com.medicology.assessment.service.grading.model.ContentBlockSnapshot;
import com.medicology.assessment.service.grading.model.GradingDecision;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RuleGrader {

    private final ObjectMapper objectMapper;

    public GradingDecision grade(ContentBlockSnapshot snapshot, String userAnswer) {
        String kind = snapshot.kind() == null ? "" : snapshot.kind();
        boolean correct = switch (kind) {
            case "ORDERING" -> isOrderingCorrectFromPayload(snapshot.payload(), userAnswer);
            case "MATCHING" -> isMatchingCorrectFromPayload(snapshot.payload(), userAnswer);
            case "QUIZ_MCQ" -> isMcqCorrect(snapshot.payload(), userAnswer);
            case "FILL_IN_THE_BLANKS" -> isFillBlanksCorrect(snapshot.payload(), userAnswer);
            case "TIMELINE" -> isTimelineCorrect(snapshot.payload(), userAnswer);
            default -> {
                String normalizedUser = normalize(userAnswer);
                String normalizedExpected = normalize(readPlainExpectedFromPayload(snapshot.payload()));
                yield normalizedExpected.equals(normalizedUser);
            }
        };
        BigDecimal points = correct ? BigDecimal.valueOf(snapshot.resolvedMaxPoints()) : BigDecimal.ZERO;
        return new GradingDecision(
                correct,
                points,
                GradingStatus.FINALIZED,
                GradingSource.RULE,
                BigDecimal.ONE,
                correct ? "Rule grader matched expected answer." : "Rule grader did not match expected answer.",
                null,
                Instant.now());
    }

    private boolean isMcqCorrect(String payload, String userAnswer) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            int idx = root.path("correctOptionIndex").asInt(-1);
            JsonNode options = root.path("options");
            if (idx < 0 || !options.isArray() || idx >= options.size()) {
                return false;
            }
            String expected = normalize(options.get(idx).asText(""));
            return !expected.isEmpty() && expected.equals(normalize(userAnswer));
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean isFillBlanksCorrect(String payload, String userAnswer) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode answers = root.path("answers");
            if (!answers.isArray() || answers.isEmpty()) {
                return false;
            }
            List<String> expected = new ArrayList<>();
            for (JsonNode n : answers) {
                expected.add(normalize(n.asText("")));
            }
            List<String> actual = parseUserStringList(userAnswer);
            if (expected.size() != actual.size()) {
                return false;
            }
            for (int i = 0; i < expected.size(); i++) {
                if (!expected.get(i).equals(actual.get(i))) {
                    return false;
                }
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private List<String> parseUserStringList(String userAnswer) {
        if (userAnswer == null || userAnswer.isBlank()) {
            return List.of();
        }
        String trimmed = userAnswer.trim();
        try {
            JsonNode node = objectMapper.readTree(trimmed);
            if (node.isArray()) {
                List<String> out = new ArrayList<>();
                for (JsonNode n : node) {
                    out.add(normalize(n.asText("")));
                }
                return out;
            }
        } catch (Exception ignored) {
            // fall through
        }
        String[] parts = trimmed.split("\\|");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            out.add(normalize(p));
        }
        return out;
    }

    private boolean isMatchingCorrectFromPayload(String payload, String userAnswer) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode pairs = root.path("pairs");
            if (!pairs.isArray()) {
                return false;
            }
            Map<String, String> expected = new LinkedHashMap<>();
            for (JsonNode item : pairs) {
                String left = normalize(item.path("left").asText(""));
                String right = normalize(item.path("right").asText(""));
                if (!left.isEmpty() && !right.isEmpty()) {
                    expected.put(left, right);
                }
            }
            Map<String, String> actual = parseMatchingMap(userAnswer);
            if (expected.isEmpty() || actual.isEmpty() || expected.size() != actual.size()) {
                return false;
            }
            for (Map.Entry<String, String> e : expected.entrySet()) {
                String ar = actual.get(e.getKey());
                if (ar == null || !ar.equals(e.getValue())) {
                    return false;
                }
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
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
            if (!left.isEmpty() && !right.isEmpty()) {
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
            if (!left.isEmpty() && !right.isEmpty()) {
                result.put(left, right);
            }
        }
        return result;
    }

    private boolean isOrderingCorrectFromPayload(String payload, String userAnswer) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode items = root.path("items");
            if (!items.isArray() || items.isEmpty()) {
                return false;
            }
            List<String> expected = new ArrayList<>();
            for (JsonNode item : items) {
                String id = item.path("id").asText("");
                if (id.isBlank()) {
                    id = normalize(item.toString());
                } else {
                    id = normalize(id);
                }
                if (!id.isEmpty()) {
                    expected.add(id);
                }
            }
            List<String> actual = parseOrderingSequence(userAnswer);
            return !expected.isEmpty() && expected.equals(actual);
        } catch (Exception ex) {
            return false;
        }
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

    private boolean isTimelineCorrect(String payload, String userAnswer) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode events = root.path("events");
            if (!events.isArray() || events.isEmpty()) {
                return false;
            }
            List<String> expected = new ArrayList<>();
            events.iterator().forEachRemaining(e -> expected.add(normalize(e.path("when").asText(""))));
            expected.sort(Comparator.naturalOrder());
            List<String> actual = parseUserStringList(userAnswer);
            actual.sort(Comparator.naturalOrder());
            return !expected.isEmpty() && expected.equals(actual);
        } catch (Exception ex) {
            return false;
        }
    }

    private String readPlainExpectedFromPayload(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root.has("sampleAnswer")) {
                return root.path("sampleAnswer").asText("");
            }
            if (root.has("correct")) {
                return root.path("correct").asText("");
            }
            if (root.isTextual()) {
                return root.asText("");
            }
            return root.toString();
        } catch (Exception ex) {
            return payload;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
