package com.medicology.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medicology.assessment.dto.request.AttemptStartRequest;
import com.medicology.assessment.dto.response.AttemptResultResponse;
import com.medicology.assessment.dto.response.AttemptStartResponse;
import com.medicology.assessment.entity.AssessmentResult;
import com.medicology.assessment.entity.Attempt;
import com.medicology.assessment.entity.AttemptAnswer;
import com.medicology.assessment.entity.AttemptStatus;
import com.medicology.assessment.entity.GradingSource;
import com.medicology.assessment.entity.GradingStatus;
import com.medicology.assessment.entity.ResultStatus;
import com.medicology.assessment.repository.AssessmentResultRepository;
import com.medicology.assessment.repository.AttemptAnswerRepository;
import com.medicology.assessment.repository.AttemptRepository;
import com.medicology.assessment.service.grading.GradingEngine;
import com.medicology.assessment.service.grading.model.ContentBlockSnapshot;
import com.medicology.assessment.service.grading.model.GradingDecision;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttemptServiceTest {

    @Mock
    private AttemptRepository attemptRepository;

    @Mock
    private AttemptAnswerRepository attemptAnswerRepository;

    @Mock
    private AssessmentResultRepository assessmentResultRepository;

    @Mock
    private GradingEngine gradingEngine;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AttemptService attemptService;

    @Test
    void submitAttempt_scoresAttempt() {
        UUID userId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        Attempt attempt = buildAttempt(userId, contentId);

        when(attemptRepository.findByIdForUpdate(attempt.getId())).thenReturn(Optional.of(attempt));
        when(attemptRepository.save(any(Attempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(assessmentResultRepository.saveAndFlush(any(AssessmentResult.class)))
                .thenAnswer(invocation -> {
                    AssessmentResult r = invocation.getArgument(0);
                    r.setAttempt(attempt);
                    attempt.setResult(r);
                    return r;
                });
        when(gradingEngine.grade(any(ContentBlockSnapshot.class), any(String.class)))
                .thenReturn(new GradingDecision(
                        true,
                        BigDecimal.valueOf(5),
                        GradingStatus.FINALIZED,
                        GradingSource.RULE,
                        BigDecimal.ONE,
                        "rule",
                        null,
                        Instant.now()));

        AttemptResultResponse response = attemptService.submitAttempt(attempt.getId(), userId);

        assertThat(response.score()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(response.maxScore()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(response.correctAnswers()).isEqualTo(1);
        assertThat(response.passed()).isTrue();
        assertThat(response.resultStatus()).isEqualTo(ResultStatus.FINAL);
    }

    @Test
    void startAttempt_usesEstimatedDurationFromRequest() {
        UUID userId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        when(attemptRepository.findTopByContentIdAndUserIdAndStatusOrderByStartedAtDesc(
                        contentId, userId, AttemptStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(attemptRepository.save(any(Attempt.class))).thenAnswer(invocation -> {
            Attempt attempt = invocation.getArgument(0);
            attempt.setId(UUID.randomUUID());
            attempt.setStartedAt(Instant.now());
            return attempt;
        });

        AttemptStartResponse response = attemptService.startAttempt(contentId, userId, new AttemptStartRequest(10));

        assertThat(response.remainingSeconds()).isEqualTo(600);
        verifyNoInteractions(gradingEngine);
    }

    @Test
    void startAttempt_fallsBackToDefaultDurationWhenRequestIsNull() {
        UUID userId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        when(attemptRepository.findTopByContentIdAndUserIdAndStatusOrderByStartedAtDesc(
                        contentId, userId, AttemptStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(attemptRepository.save(any(Attempt.class))).thenAnswer(invocation -> {
            Attempt attempt = invocation.getArgument(0);
            attempt.setId(UUID.randomUUID());
            attempt.setStartedAt(Instant.now());
            return attempt;
        });

        AttemptStartResponse response = attemptService.startAttempt(contentId, userId, null);

        assertThat(response.remainingSeconds()).isEqualTo(7 * 60);
    }

    @Test
    void submitAttempt_returnsExistingResultWhenAlreadySubmitted() {
        UUID userId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        Attempt attempt = buildAttempt(userId, contentId);
        AssessmentResult result = new AssessmentResult();
        result.setAttempt(attempt);
        result.setScore(BigDecimal.valueOf(5));
        result.setMaxScore(BigDecimal.valueOf(5));
        result.setCorrectAnswers(1);
        result.setTotalQuestions(1);
        result.setPassed(true);
        result.setCompletedAt(Instant.now());

        attempt.setStatus(AttemptStatus.FINALIZED);
        attempt.setResult(result);

        when(attemptRepository.findByIdForUpdate(attempt.getId())).thenReturn(Optional.of(attempt));

        AttemptResultResponse response = attemptService.submitAttempt(attempt.getId(), userId);

        assertThat(response.passed()).isTrue();
        verifyNoInteractions(gradingEngine);
    }

    private Attempt buildAttempt(UUID userId, UUID contentId) {
        Attempt attempt = new Attempt();
        attempt.setId(UUID.randomUUID());
        attempt.setUserId(userId);
        attempt.setContentId(contentId);
        attempt.setRemainingSeconds(600);
        attempt.setStatus(AttemptStatus.IN_PROGRESS);
        attempt.setStartedAt(Instant.now());

        AttemptAnswer answer = new AttemptAnswer();
        answer.setAttempt(attempt);
        answer.setContentBlockId(UUID.randomUUID());
        answer.setKindSnapshot("QUIZ_MCQ");
        answer.setPayloadSnapshot("{}");
        answer.setMaxScoreSnapshot(5);
        answer.setBlockOrderIndex(1);
        answer.setUserAnswer("A");
        answer.setAnsweredAt(Instant.now());

        attempt.setAnswers(new ArrayList<>(List.of(answer)));
        return attempt;
    }
}
