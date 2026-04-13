package com.medicology.assessment.service;

import com.medicology.assessment.dto.request.AttemptAnswerRequest;
import com.medicology.assessment.dto.request.LearningProgressSyncRequest;
import com.medicology.assessment.dto.response.AttemptAnswerResponse;
import com.medicology.assessment.dto.response.AttemptQuestionOptionResponse;
import com.medicology.assessment.dto.response.AttemptQuestionResponse;
import com.medicology.assessment.dto.response.AttemptResultResponse;
import com.medicology.assessment.dto.response.AttemptStartResponse;
import com.medicology.assessment.dto.response.AttemptSummaryResponse;
import com.medicology.assessment.entity.Assessment;
import com.medicology.assessment.entity.AssessmentResult;
import com.medicology.assessment.entity.AssessmentStatus;
import com.medicology.assessment.entity.Attempt;
import com.medicology.assessment.entity.AttemptAnswer;
import com.medicology.assessment.entity.AttemptStatus;
import com.medicology.assessment.entity.Question;
import com.medicology.assessment.entity.QuestionOption;
import com.medicology.assessment.exception.ConflictException;
import com.medicology.assessment.exception.NotFoundException;
import com.medicology.assessment.repository.AssessmentRepository;
import com.medicology.assessment.repository.AssessmentResultRepository;
import com.medicology.assessment.repository.AttemptAnswerRepository;
import com.medicology.assessment.repository.AttemptRepository;
import com.medicology.assessment.repository.QuestionOptionRepository;
import com.medicology.assessment.repository.QuestionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AttemptService {

    private final AssessmentRepository assessmentRepository;
    private final AttemptRepository attemptRepository;
    private final AttemptAnswerRepository attemptAnswerRepository;
    private final AssessmentResultRepository assessmentResultRepository;
    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final LearningProgressGateway learningProgressGateway;
    private final LearningEnrollmentClient learningEnrollmentClient;

    public AttemptStartResponse startAttempt(UUID assessmentId, UUID userId) {
        Assessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new NotFoundException(1404, "Assessment not found: " + assessmentId));

        if (assessment.getStatus() != AssessmentStatus.PUBLISHED || !Boolean.TRUE.equals(assessment.getActive())) {
            throw new ConflictException(1409, "Assessment is not available for submission.");
        }

        learningEnrollmentClient.assertCanAccessAssessment(userId, assessment.getSectionId(), assessment.getLessonId());

        Attempt existingAttempt = attemptRepository
                .findTopByAssessment_IdAndUserIdAndStatusOrderByStartedAtDesc(assessmentId, userId, AttemptStatus.IN_PROGRESS)
                .orElse(null);
        if (existingAttempt != null) {
            return toStartResponse(existingAttempt);
        }

        Attempt attempt = new Attempt();
        attempt.setAssessment(assessment);
        attempt.setUserId(userId);
        attempt.setCourseId(assessment.getCourseId());
        attempt.setSectionId(assessment.getSectionId());
        attempt.setLessonId(assessment.getLessonId());
        attempt.setStatus(AttemptStatus.IN_PROGRESS);

        return toStartResponse(attemptRepository.save(attempt));
    }

    public AttemptAnswerResponse saveAnswer(UUID attemptId, UUID userId, AttemptAnswerRequest request) {
        Attempt attempt = findOwnedAttempt(attemptId, userId);
        ensureInProgress(attempt);
        ensureWithinTimeLimit(attempt);

        Question question = questionRepository.findByIdAndAssessment_Id(request.questionId(), attempt.getAssessment().getId())
                .orElseThrow(() -> new NotFoundException(1404, "Question not found for attempt."));
        QuestionOption selectedOption = questionOptionRepository.findByIdAndQuestion_Id(request.selectedOptionId(), question.getId())
                .orElseThrow(() -> new NotFoundException(1404, "Selected option not found for question."));

        AttemptAnswer answer = attemptAnswerRepository.findByAttempt_IdAndQuestion_Id(attemptId, question.getId())
                .orElseGet(() -> {
                    AttemptAnswer created = new AttemptAnswer();
                    created.setAttempt(attempt);
                    created.setQuestion(question);
                    attempt.addAnswer(created);
                    return created;
                });

        answer.setSelectedOption(selectedOption);
        AttemptAnswer persisted = attemptAnswerRepository.save(answer);

        return new AttemptAnswerResponse(
                persisted.getAttempt().getId(),
                persisted.getQuestion().getId(),
                persisted.getSelectedOption().getId(),
                persisted.getAnsweredAt());
    }

    public AttemptResultResponse submitAttempt(UUID attemptId, UUID userId) {
        Attempt attempt = findOwnedAttempt(attemptId, userId);

        if (attempt.getStatus() == AttemptStatus.SUBMITTED && attempt.getResult() != null) {
            return toResultResponse(attempt.getResult());
        }

        ensureInProgress(attempt);
        ensureWithinTimeLimit(attempt);

        List<Question> activeQuestions = attempt.getAssessment().getQuestions().stream()
                .filter(question -> Boolean.TRUE.equals(question.getActive()))
                .sorted(Comparator.comparing(Question::getDisplayOrder))
                .toList();
        Map<UUID, AttemptAnswer> answerByQuestionId = attempt.getAnswers().stream()
                .collect(Collectors.toMap(answer -> answer.getQuestion().getId(), Function.identity(), (left, right) -> right));

        BigDecimal maxScore = activeQuestions.stream()
                .map(question -> BigDecimal.valueOf(question.getPoints()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal score = BigDecimal.ZERO;
        int correctAnswers = 0;

        for (Question question : activeQuestions) {
            AttemptAnswer answer = answerByQuestionId.get(question.getId());
            if (answer == null) {
                continue;
            }

            boolean correct = Boolean.TRUE.equals(answer.getSelectedOption().getCorrect());
            answer.setCorrect(correct);
            answer.setAwardedPoints(correct ? BigDecimal.valueOf(question.getPoints()) : BigDecimal.ZERO);

            if (correct) {
                correctAnswers++;
                score = score.add(answer.getAwardedPoints());
            }
        }

        attemptAnswerRepository.saveAll(attempt.getAnswers());

        AssessmentResult result = attempt.getResult();
        if (result == null) {
            result = new AssessmentResult();
            result.setAttempt(attempt);
            attempt.setResult(result);
        }

        result.setScore(score);
        result.setMaxScore(maxScore);
        result.setCorrectAnswers(correctAnswers);
        result.setTotalQuestions(activeQuestions.size());
        result.setPassed(score.compareTo(BigDecimal.valueOf(attempt.getAssessment().getPassScore())) >= 0);
        result.setCompletedAt(Instant.now());

        attempt.setStatus(AttemptStatus.SUBMITTED);
        attempt.setSubmittedAt(result.getCompletedAt());

        attemptRepository.save(attempt);
        assessmentResultRepository.save(result);

        learningProgressGateway.publishAssessmentResult(new LearningProgressSyncRequest(
                attempt.getUserId(),
                attempt.getCourseId(),
                attempt.getSectionId(),
                attempt.getAssessment().getId(),
                attempt.getId(),
                result.getScore(),
                result.getPassed(),
                result.getCompletedAt()));

        return toResultResponse(result);
    }

    @Transactional(readOnly = true)
    public AttemptResultResponse getResult(UUID attemptId, UUID userId) {
        Attempt attempt = findOwnedAttempt(attemptId, userId);
        if (attempt.getResult() == null) {
            throw new ConflictException(1409, "Attempt has not been submitted yet.");
        }
        return toResultResponse(attempt.getResult());
    }

    @Transactional(readOnly = true)
    public List<AttemptSummaryResponse> getMyAttempts(UUID userId) {
        return attemptRepository.findAllByUserIdOrderByStartedAtDesc(userId)
                .stream()
                .map(this::toAttemptSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AttemptSummaryResponse> getAllAttempts() {
        return attemptRepository.findAll(Sort.by(Sort.Direction.DESC, "startedAt"))
                .stream()
                .map(this::toAttemptSummary)
                .toList();
    }

    private Attempt findOwnedAttempt(UUID attemptId, UUID userId) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new NotFoundException(1404, "Attempt not found: " + attemptId));

        if (!attempt.getUserId().equals(userId)) {
            throw new NotFoundException(1404, "Attempt not found for current user.");
        }

        return attempt;
    }

    private void ensureInProgress(Attempt attempt) {
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new ConflictException(1409, "Attempt is already submitted.");
        }
    }

    private void ensureWithinTimeLimit(Attempt attempt) {
        Integer limit = attempt.getAssessment().getTimeLimitMinutes();
        if (limit == null || limit <= 0) {
            return;
        }
        Instant deadline = attempt.getStartedAt().plus(limit, ChronoUnit.MINUTES);
        if (Instant.now().isAfter(deadline)) {
            throw new ConflictException(1409, "Time limit for this attempt has expired.");
        }
    }

    private AttemptStartResponse toStartResponse(Attempt attempt) {
        return new AttemptStartResponse(
                attempt.getId(),
                attempt.getAssessment().getId(),
                attempt.getAssessment().getTitle(),
                attempt.getStatus(),
                attempt.getStartedAt(),
                attempt.getAssessment().getQuestions().stream()
                        .filter(question -> Boolean.TRUE.equals(question.getActive()))
                        .sorted(Comparator.comparing(Question::getDisplayOrder))
                        .map(this::toAttemptQuestionResponse)
                        .toList());
    }

    private AttemptQuestionResponse toAttemptQuestionResponse(Question question) {
        return new AttemptQuestionResponse(
                question.getId(),
                question.getContent(),
                question.getType(),
                question.getDisplayOrder(),
                question.getPoints(),
                question.getOptions().stream()
                        .sorted(Comparator.comparing(QuestionOption::getDisplayOrder))
                        .map(option -> new AttemptQuestionOptionResponse(
                                option.getId(),
                                option.getContent(),
                                option.getDisplayOrder()))
                        .toList());
    }

    private AttemptResultResponse toResultResponse(AssessmentResult result) {
        return new AttemptResultResponse(
                result.getAttempt().getId(),
                result.getAttempt().getAssessment().getId(),
                result.getScore(),
                result.getMaxScore(),
                result.getCorrectAnswers(),
                result.getTotalQuestions(),
                result.getPassed(),
                result.getCompletedAt());
    }

    private AttemptSummaryResponse toAttemptSummary(Attempt attempt) {
        AssessmentResult result = attempt.getResult();
        return new AttemptSummaryResponse(
                attempt.getId(),
                attempt.getAssessment().getId(),
                attempt.getAssessment().getTitle(),
                attempt.getStatus(),
                attempt.getStartedAt(),
                attempt.getSubmittedAt(),
                result == null ? BigDecimal.ZERO : result.getScore(),
                result != null && Boolean.TRUE.equals(result.getPassed()));
    }
}
