package com.medicology.assessment.service;

import com.medicology.assessment.dto.request.AttemptAnswerRequest;
import com.medicology.assessment.dto.request.LearningProgressSyncRequest;
import com.medicology.assessment.dto.response.AttemptAnswerResponse;
import com.medicology.assessment.dto.response.AttemptQuestionResponse;
import com.medicology.assessment.dto.response.AttemptReviewAnswerResponse;
import com.medicology.assessment.dto.response.AttemptReviewResponse;
import com.medicology.assessment.dto.response.AttemptResultResponse;
import com.medicology.assessment.dto.response.AttemptStartResponse;
import com.medicology.assessment.dto.response.AttemptSummaryResponse;
import com.medicology.assessment.entity.Assessment;
import com.medicology.assessment.entity.AssessmentResult;
import com.medicology.assessment.entity.AssessmentStatus;
import com.medicology.assessment.entity.Attempt;
import com.medicology.assessment.entity.AttemptAnswer;
import com.medicology.assessment.entity.AttemptStatus;
import com.medicology.assessment.entity.GradingStatus;
import com.medicology.assessment.entity.Question;
import com.medicology.assessment.entity.ResultStatus;
import com.medicology.assessment.exception.ConflictException;
import com.medicology.assessment.exception.NotFoundException;
import com.medicology.assessment.repository.AssessmentRepository;
import com.medicology.assessment.repository.AssessmentResultRepository;
import com.medicology.assessment.repository.AttemptAnswerRepository;
import com.medicology.assessment.repository.AttemptRepository;
import com.medicology.assessment.repository.QuestionRepository;
import com.medicology.assessment.service.grading.GradingEngine;
import com.medicology.assessment.service.grading.model.GradingDecision;
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
    private final GradingEngine gradingEngine;
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

        AttemptAnswer answer = attemptAnswerRepository.findByAttempt_IdAndQuestion_Id(attemptId, question.getId())
                .orElseGet(() -> {
                    AttemptAnswer created = new AttemptAnswer();
                    created.setAttempt(attempt);
                    created.setQuestion(question);
                    attempt.addAnswer(created);
                    return created;
                });

        answer.setUserAnswer(request.userAnswer());
        answer.setQuestionVersion(question.getVersion());
        answer.setPayloadSnapshot(question.getPayload());
        answer.setAnswerKeySnapshot(question.getAnswerKey());
        answer.setOptionContentSnapshot(question.getPayload());
        answer.setGradingStatus(GradingStatus.PENDING);
        answer.setGradingSource(null);
        answer.setConfidence(null);
        answer.setExplanation(null);
        answer.setAiModel(null);
        answer.setEvaluatedAt(null);
        answer.setEvaluatedBy(null);
        answer.setCorrect(Boolean.FALSE);
        answer.setAwardedPoints(BigDecimal.ZERO);
        AttemptAnswer persisted = attemptAnswerRepository.save(answer);

        return new AttemptAnswerResponse(
                persisted.getAttempt().getId(),
                persisted.getQuestion().getId(),
                persisted.getUserAnswer(),
                persisted.getGradingStatus(),
                persisted.getAnsweredAt());
    }

    public AttemptResultResponse submitAttempt(UUID attemptId, UUID userId) {
        Attempt attempt = findOwnedAttempt(attemptId, userId);

        if ((attempt.getStatus() == AttemptStatus.SUBMITTED
                || attempt.getStatus() == AttemptStatus.FINALIZED
                || attempt.getStatus() == AttemptStatus.PENDING_REVIEW)
                && attempt.getResult() != null) {
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
        int pendingManualReviews = 0;

        for (Question question : activeQuestions) {
            AttemptAnswer answer = answerByQuestionId.get(question.getId());
            if (answer == null) {
                continue;
            }

            GradingDecision decision = gradingEngine.grade(question, answer.getUserAnswer());
            answer.setCorrect(decision.correct());
            answer.setAwardedPoints(decision.awardedPoints());
            answer.setGradingStatus(decision.gradingStatus());
            answer.setGradingSource(decision.gradingSource());
            answer.setConfidence(decision.confidence());
            answer.setExplanation(decision.explanation());
            answer.setAiModel(decision.aiModel());
            answer.setEvaluatedAt(decision.evaluatedAt());

            if (decision.gradingStatus() == GradingStatus.MANUAL_REVIEW) {
                pendingManualReviews++;
                continue;
            }

            if (decision.correct()) {
                correctAnswers++;
            }
            score = score.add(answer.getAwardedPoints());
        }

        if (pendingManualReviews > 0) {
            attempt.setStatus(AttemptStatus.PENDING_REVIEW);
        } else {
            attempt.setStatus(AttemptStatus.FINALIZED);
            attempt.setSubmittedAt(Instant.now());
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
        result.setResultStatus(pendingManualReviews > 0 ? ResultStatus.PROVISIONAL : ResultStatus.FINAL);

        attemptRepository.save(attempt);
        assessmentResultRepository.save(result);

        if (result.getResultStatus() == ResultStatus.FINAL) {
            learningProgressGateway.publishAssessmentResult(new LearningProgressSyncRequest(
                    attempt.getUserId(),
                    attempt.getCourseId(),
                    attempt.getSectionId(),
                    attempt.getAssessment().getId(),
                    attempt.getId(),
                    result.getScore(),
                    result.getPassed(),
                    result.getResultStatus().name(),
                    result.getCompletedAt()));
        }

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
    public AttemptReviewResponse getReview(UUID attemptId, UUID userId) {
        Attempt attempt = findOwnedAttempt(attemptId, userId);
        if (attempt.getResult() == null) {
            throw new ConflictException(1409, "Attempt has not been submitted yet.");
        }
        AssessmentResult result = attempt.getResult();
        Map<UUID, AttemptAnswer> answerByQuestion = attempt.getAnswers().stream()
                .collect(Collectors.toMap(answer -> answer.getQuestion().getId(), Function.identity(), (left, right) -> right));
        List<AttemptReviewAnswerResponse> answers = attempt.getAssessment().getQuestions().stream()
                .filter(question -> Boolean.TRUE.equals(question.getActive()))
                .sorted(Comparator.comparing(Question::getDisplayOrder))
                .map(question -> {
                    AttemptAnswer answer = answerByQuestion.get(question.getId());
                    if (answer == null) {
                        return new AttemptReviewAnswerResponse(
                                question.getId(),
                                question.getContent(),
                                question.getType(),
                                question.getDisplayOrder(),
                                question.getPoints(),
                                question.getPayload(),
                                null,
                                null,
                                BigDecimal.ZERO,
                                GradingStatus.PENDING,
                                null,
                                null,
                                null,
                                null);
                    }
                    return new AttemptReviewAnswerResponse(
                            question.getId(),
                            question.getContent(),
                            question.getType(),
                            question.getDisplayOrder(),
                            question.getPoints(),
                            answer.getPayloadSnapshot() == null ? question.getPayload() : answer.getPayloadSnapshot(),
                            answer.getUserAnswer(),
                            answer.getCorrect(),
                            answer.getAwardedPoints(),
                            answer.getGradingStatus(),
                            answer.getGradingSource(),
                            answer.getConfidence(),
                            answer.getExplanation(),
                            answer.getAiModel());
                })
                .toList();

        return new AttemptReviewResponse(
                result.getAttempt().getId(),
                result.getAttempt().getAssessment().getId(),
                result.getAttempt().getAssessment().getTitle(),
                result.getScore(),
                result.getMaxScore(),
                result.getCorrectAnswers(),
                result.getTotalQuestions(),
                result.getPassed(),
                result.getCompletedAt(),
                result.getResultStatus(),
                result.getAttempt().getStatus(),
                (int) answers.stream().filter(item -> item.gradingStatus() == GradingStatus.MANUAL_REVIEW).count(),
                answers);
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

    public AttemptResultResponse refreshResultAfterManualReview(UUID attemptId) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new NotFoundException(1404, "Attempt not found: " + attemptId));

        List<Question> activeQuestions = attempt.getAssessment().getQuestions().stream()
                .filter(question -> Boolean.TRUE.equals(question.getActive()))
                .toList();

        BigDecimal score = BigDecimal.ZERO;
        int correctAnswers = 0;
        int pendingManualReviews = 0;

        for (AttemptAnswer answer : attempt.getAnswers()) {
            if (answer.getGradingStatus() == GradingStatus.MANUAL_REVIEW || answer.getGradingStatus() == GradingStatus.PENDING) {
                pendingManualReviews++;
                continue;
            }
            score = score.add(answer.getAwardedPoints());
            if (Boolean.TRUE.equals(answer.getCorrect())) {
                correctAnswers++;
            }
        }

        AssessmentResult result = attempt.getResult();
        if (result == null) {
            result = new AssessmentResult();
            result.setAttempt(attempt);
            attempt.setResult(result);
        }

        result.setScore(score);
        result.setMaxScore(activeQuestions.stream()
                .map(question -> BigDecimal.valueOf(question.getPoints()))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        result.setCorrectAnswers(correctAnswers);
        result.setTotalQuestions(activeQuestions.size());
        result.setPassed(score.compareTo(BigDecimal.valueOf(attempt.getAssessment().getPassScore())) >= 0);
        result.setCompletedAt(Instant.now());
        result.setResultStatus(pendingManualReviews > 0 ? ResultStatus.PROVISIONAL : ResultStatus.FINAL);

        if (pendingManualReviews > 0) {
            attempt.setStatus(AttemptStatus.PENDING_REVIEW);
        } else {
            attempt.setStatus(AttemptStatus.FINALIZED);
            attempt.setSubmittedAt(result.getCompletedAt());
            learningProgressGateway.publishAssessmentResult(new LearningProgressSyncRequest(
                    attempt.getUserId(),
                    attempt.getCourseId(),
                    attempt.getSectionId(),
                    attempt.getAssessment().getId(),
                    attempt.getId(),
                    result.getScore(),
                    result.getPassed(),
                    result.getResultStatus().name(),
                    result.getCompletedAt()));
        }

        attemptRepository.save(attempt);
        assessmentResultRepository.save(result);
        return toResultResponse(result);
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
            throw new ConflictException(1409, "Attempt is not in progress.");
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
                question.getPayload(),
                question.getVersion());
    }

    private AttemptResultResponse toResultResponse(AssessmentResult result) {
        long pendingManualReviews = result.getAttempt().getAnswers().stream()
                .filter(answer -> answer.getGradingStatus() == GradingStatus.MANUAL_REVIEW)
                .count();
        return new AttemptResultResponse(
                result.getAttempt().getId(),
                result.getAttempt().getAssessment().getId(),
                result.getScore(),
                result.getMaxScore(),
                result.getCorrectAnswers(),
                result.getTotalQuestions(),
                result.getPassed(),
                result.getCompletedAt(),
                result.getResultStatus(),
                result.getAttempt().getStatus(),
                (int) pendingManualReviews);
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
