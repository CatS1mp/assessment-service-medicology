package com.medicology.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medicology.assessment.dto.response.AttemptResultResponse;
import com.medicology.assessment.entity.Assessment;
import com.medicology.assessment.entity.AssessmentResult;
import com.medicology.assessment.entity.AssessmentStatus;
import com.medicology.assessment.entity.Attempt;
import com.medicology.assessment.entity.AttemptAnswer;
import com.medicology.assessment.entity.AttemptStatus;
import com.medicology.assessment.entity.Question;
import com.medicology.assessment.entity.QuestionOption;
import com.medicology.assessment.entity.QuestionType;
import com.medicology.assessment.repository.AssessmentRepository;
import com.medicology.assessment.repository.AssessmentResultRepository;
import com.medicology.assessment.repository.AttemptAnswerRepository;
import com.medicology.assessment.repository.AttemptRepository;
import com.medicology.assessment.repository.QuestionOptionRepository;
import com.medicology.assessment.repository.QuestionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttemptServiceTest {

    @Mock
    private AssessmentRepository assessmentRepository;

    @Mock
    private AttemptRepository attemptRepository;

    @Mock
    private AttemptAnswerRepository attemptAnswerRepository;

    @Mock
    private AssessmentResultRepository assessmentResultRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private QuestionOptionRepository questionOptionRepository;

    @Mock
    private LearningProgressGateway learningProgressGateway;

    @InjectMocks
    private AttemptService attemptService;

    @Test
    void submitAttempt_scoresAttemptAndPublishesLearningProgress() {
        UUID userId = UUID.randomUUID();
        Assessment assessment = buildAssessment();
        Attempt attempt = buildAttempt(userId, assessment);

        when(attemptRepository.findById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(attemptRepository.save(any(Attempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(assessmentResultRepository.save(any(AssessmentResult.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AttemptResultResponse response = attemptService.submitAttempt(attempt.getId(), userId);

        assertThat(response.score()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(response.maxScore()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(response.correctAnswers()).isEqualTo(1);
        assertThat(response.passed()).isTrue();
        verify(learningProgressGateway).publishAssessmentResult(any());
    }

    @Test
    void submitAttempt_returnsExistingResultWhenAlreadySubmitted() {
        UUID userId = UUID.randomUUID();
        Assessment assessment = buildAssessment();
        Attempt attempt = buildAttempt(userId, assessment);
        AssessmentResult result = new AssessmentResult();
        result.setAttempt(attempt);
        result.setScore(BigDecimal.valueOf(5));
        result.setMaxScore(BigDecimal.valueOf(5));
        result.setCorrectAnswers(1);
        result.setTotalQuestions(1);
        result.setPassed(true);
        result.setCompletedAt(Instant.now());

        attempt.setStatus(AttemptStatus.SUBMITTED);
        attempt.setResult(result);

        when(attemptRepository.findById(attempt.getId())).thenReturn(Optional.of(attempt));

        AttemptResultResponse response = attemptService.submitAttempt(attempt.getId(), userId);

        assertThat(response.passed()).isTrue();
        verify(learningProgressGateway, never()).publishAssessmentResult(any());
    }

    private Assessment buildAssessment() {
        Assessment assessment = new Assessment();
        assessment.setId(UUID.randomUUID());
        assessment.setTitle("Emergency Quiz");
        assessment.setCourseId(UUID.randomUUID());
        assessment.setSectionId(UUID.randomUUID());
        assessment.setPassScore(4);
        assessment.setStatus(AssessmentStatus.PUBLISHED);
        assessment.setActive(true);

        Question question = new Question();
        question.setId(UUID.randomUUID());
        question.setAssessment(assessment);
        question.setContent("Correct option?");
        question.setType(QuestionType.SINGLE_CHOICE);
        question.setDisplayOrder(1);
        question.setPoints(5);
        question.setActive(true);

        QuestionOption correctOption = new QuestionOption();
        correctOption.setId(UUID.randomUUID());
        correctOption.setQuestion(question);
        correctOption.setContent("A");
        correctOption.setCorrect(true);
        correctOption.setDisplayOrder(1);

        question.setOptions(List.of(correctOption));
        assessment.setQuestions(List.of(question));
        return assessment;
    }

    private Attempt buildAttempt(UUID userId, Assessment assessment) {
        Attempt attempt = new Attempt();
        attempt.setId(UUID.randomUUID());
        attempt.setAssessment(assessment);
        attempt.setUserId(userId);
        attempt.setCourseId(assessment.getCourseId());
        attempt.setSectionId(assessment.getSectionId());
        attempt.setStatus(AttemptStatus.IN_PROGRESS);
        attempt.setStartedAt(Instant.now());

        AttemptAnswer answer = new AttemptAnswer();
        answer.setAttempt(attempt);
        answer.setQuestion(assessment.getQuestions().get(0));
        answer.setSelectedOption(assessment.getQuestions().get(0).getOptions().get(0));
        answer.setAnsweredAt(Instant.now());

        attempt.setAnswers(List.of(answer));
        return attempt;
    }
}
