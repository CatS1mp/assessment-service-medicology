package com.medicology.assessment.service.grading;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medicology.assessment.config.AssessmentProperties;
import com.medicology.assessment.entity.GradingSource;
import com.medicology.assessment.entity.GradingStatus;
import com.medicology.assessment.service.grading.model.ContentBlockSnapshot;
import com.medicology.assessment.service.grading.model.GradingDecision;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AiShortAnswerGraderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void teardown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void grade_returnsFinalizedWhenApiKeyMissing() {
        AssessmentProperties properties = new AssessmentProperties();
        properties.setAiModel("gemini-2.5-flash");
        properties.setAiApiKey("");
        properties.setAiConfidenceThreshold(0.8d);

        AiShortAnswerGrader grader = new AiShortAnswerGrader(properties, objectMapper);
        GradingDecision decision = grader.grade(buildSnapshot(), "Sample learner answer");

        assertThat(decision.gradingStatus()).isEqualTo(GradingStatus.FINALIZED);
        assertThat(decision.gradingSource()).isEqualTo(GradingSource.AI);
        assertThat(decision.correct()).isFalse();
    }

    @Test
    void grade_returnsFinalizedWhenProviderConfidencePassesThreshold() throws Exception {
        startMockServer("""
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "{\\"correct\\":true,\\"confidence\\":0.91,\\"explanation\\":\\"Good reasoning\\",\\"suggestedCorrectAnswers\\":[]}"
                          }
                        ]
                      }
                    }
                  ]
                }
                """);

        AssessmentProperties properties = new AssessmentProperties();
        properties.setAiModel("gemini-2.5-flash");
        properties.setAiApiKey("demo-key");
        properties.setAiEndpoint("http://localhost:" + server.getAddress().getPort() + "/ai-eval");
        properties.setAiConfidenceThreshold(0.8d);

        AiShortAnswerGrader grader = new AiShortAnswerGrader(properties, objectMapper);
        GradingDecision decision = grader.grade(buildSnapshot(), "Learner answer");

        assertThat(decision.gradingStatus()).isEqualTo(GradingStatus.FINALIZED);
        assertThat(decision.gradingSource()).isEqualTo(GradingSource.AI);
        assertThat(decision.correct()).isTrue();
        assertThat(decision.confidence()).isEqualByComparingTo(new BigDecimal("0.91"));
    }

    @Test
    void grade_returnsManualReviewWhenProviderConfidenceInReviewBand() throws Exception {
        startMockServer("""
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "{\\"correct\\":false,\\"confidence\\":0.75,\\"explanation\\":\\"Unclear answer\\",\\"suggestedCorrectAnswers\\":[\\"...\\"]}"
                          }
                        ]
                      }
                    }
                  ]
                }
                """);

        AssessmentProperties properties = new AssessmentProperties();
        properties.setAiModel("gemini-2.5-flash");
        properties.setAiApiKey("demo-key");
        properties.setAiEndpoint("http://localhost:" + server.getAddress().getPort() + "/ai-eval");
        properties.setAiConfidenceThreshold(0.8d);

        AiShortAnswerGrader grader = new AiShortAnswerGrader(properties, objectMapper);
        GradingDecision decision = grader.grade(buildSnapshot(), "Learner answer");

        assertThat(decision.gradingStatus()).isEqualTo(GradingStatus.MANUAL_REVIEW);
        assertThat(decision.gradingSource()).isNull();
    }

    private ContentBlockSnapshot buildSnapshot() {
        return new ContentBlockSnapshot(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "SHORT_ANSWER",
                "{\"prompt\":\"Explain why airway assessment is important.\",\"sampleAnswer\":\"Airway assessment ensures oxygen can reach lungs.\"}",
                5,
                1,
                true);
    }

    private void startMockServer(String responseBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/ai-eval", exchange -> writeResponse(exchange, responseBody));
        server.start();
    }

    private void writeResponse(HttpExchange exchange, String responseBody) throws IOException {
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }
}
