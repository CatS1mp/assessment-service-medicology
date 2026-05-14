package com.medicology.assessment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medicology.assessment.dto.request.AttemptAnswerRequest;
import com.medicology.assessment.dto.request.AttemptStartRequest;
import com.medicology.assessment.dto.request.AttemptTickRequest;
import com.medicology.assessment.dto.response.AttemptAnswerLookupResponse;
import com.medicology.assessment.dto.response.AttemptAnswerResponse;
import com.medicology.assessment.dto.response.AttemptInProgressItemResponse;
import com.medicology.assessment.dto.response.AttemptResultResponse;
import com.medicology.assessment.dto.response.AttemptReviewAnswerResponse;
import com.medicology.assessment.dto.response.AttemptReviewResponse;
import com.medicology.assessment.dto.response.AttemptStartResponse;
import com.medicology.assessment.dto.response.AttemptSummaryResponse;
import com.medicology.assessment.dto.response.AttemptTickResponse;
import com.medicology.assessment.entity.AssessmentResult;
import com.medicology.assessment.entity.Attempt;
import com.medicology.assessment.entity.AttemptAnswer;
import com.medicology.assessment.entity.AttemptStatus;
import com.medicology.assessment.entity.GradingStatus;
import com.medicology.assessment.entity.ResultStatus;
import com.medicology.assessment.exception.ConflictException;
import com.medicology.assessment.exception.NotFoundException;
import com.medicology.assessment.repository.AssessmentResultRepository;
import com.medicology.assessment.repository.AttemptAnswerRepository;
import com.medicology.assessment.repository.AttemptRepository;
import com.medicology.assessment.service.grading.GradingEngine;
import com.medicology.assessment.service.grading.model.ContentBlockSnapshot;
import com.medicology.assessment.service.grading.model.GradingDecision;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AttemptService {

    private static final BigDecimal PASS_RATIO = new BigDecimal("0.5");
    private static final int DEFAULT_DURATION_MINUTES = 7;

    private final AttemptRepository attemptRepository;
    private final AttemptAnswerRepository attemptAnswerRepository;
    private final AssessmentResultRepository assessmentResultRepository;
    private final GradingEngine gradingEngine;
    private final ObjectMapper objectMapper;

    public AttemptStartResponse startAttempt(UUID contentId, UUID userId, AttemptStartRequest request) {
        return attemptRepository
                .findTopByContentIdAndUserIdAndStatusOrderByStartedAtDesc(contentId, userId, AttemptStatus.IN_PROGRESS)
                .map(a -> new AttemptStartResponse(
                        a.getId(), contentId, a.getStatus(), a.getStartedAt(), a.getRemainingSeconds()))
                .orElseGet(() -> {
                    int minutes = request != null && request.estimatedDurationMinutes() != null
                            ? Math.max(1, request.estimatedDurationMinutes())
                            : DEFAULT_DURATION_MINUTES;
                    Attempt attempt = new Attempt();
                    attempt.setUserId(userId);
                    attempt.setContentId(contentId);
                    attempt.setRemainingSeconds(minutes * 60);
                    attempt.setStatus(AttemptStatus.IN_PROGRESS);
                    Attempt saved = attemptRepository.save(attempt);
                    return new AttemptStartResponse(
                            saved.getId(), contentId, saved.getStatus(), saved.getStartedAt(), saved.getRemainingSeconds());
                });
    }

    public AttemptAnswerResponse saveAnswer(UUID attemptId, UUID userId, AttemptAnswerRequest request) {
        Attempt attempt = findOwnedAttempt(attemptId, userId);
        ensureInProgress(attempt);
        ensureTimeRemaining(attempt);

        if (!request.contentId().equals(attempt.getContentId())) {
            throw new ConflictException(1409, "Content block does not belong to this attempt's content.");
        }
        if (Boolean.FALSE.equals(request.isGradable())) {
            throw new ConflictException(1409, "This block is not gradable.");
        }

        AttemptAnswer answer = attemptAnswerRepository
                .findByAttempt_IdAndContentBlockId(attemptId, request.contentBlockId())
                .orElseGet(() -> {
                    AttemptAnswer created = new AttemptAnswer();
                    created.setAttempt(attempt);
                    created.setContentBlockId(request.contentBlockId());
                    attempt.addAnswer(created);
                    return created;
                });

        ContentBlockSnapshot snap = new ContentBlockSnapshot(
                request.contentBlockId(),
                request.contentId(),
                request.kind(),
                request.payload(),
                request.maxScore(),
                request.orderIndex(),
                request.isGradable() == null ? Boolean.TRUE : request.isGradable());

        answer.setKindSnapshot(snap.kind());
        answer.setBlockOrderIndex(snap.orderIndex());
        answer.setPromptSnapshot(extractPrompt(snap.payload(), snap.kind()));
        answer.setMaxScoreSnapshot(snap.resolvedMaxPoints());
        answer.setUserAnswer(request.userAnswer());
        answer.setPayloadSnapshot(snap.payload());
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
                request.contentBlockId(),
                persisted.getUserAnswer(),
                persisted.getGradingStatus(),
                persisted.getAnsweredAt());
    }

    @Transactional(readOnly = true)
    public AttemptAnswerLookupResponse getAnswer(UUID attemptId, UUID contentBlockId, UUID userId) {
        findOwnedAttempt(attemptId, userId);
        return attemptAnswerRepository
                .findByAttempt_IdAndContentBlockId(attemptId, contentBlockId)
                .map(a -> new AttemptAnswerLookupResponse(a.getUserAnswer()))
                .orElseGet(() -> new AttemptAnswerLookupResponse(null));
    }

    public AttemptTickResponse tickRemaining(UUID attemptId, UUID userId, AttemptTickRequest request) {
        int delta = request == null || request.deltaSeconds() == null || request.deltaSeconds() < 1
                ? 60
                : request.deltaSeconds();
        Attempt attempt = findOwnedAttemptForUpdate(attemptId, userId);
        ensureInProgress(attempt);
        int next = Math.max(0, attempt.getRemainingSeconds() - delta);
        attempt.setRemainingSeconds(next);
        attemptRepository.saveAndFlush(attempt);
        if (next <= 0) {
            submitAttempt(attemptId, userId);
            Attempt updated = findOwnedAttempt(attemptId, userId);
            return new AttemptTickResponse(updated.getRemainingSeconds());
        }
        return new AttemptTickResponse(next);
    }

    @Transactional(readOnly = true)
    public List<AttemptInProgressItemResponse> getInProgressAttempts(UUID userId) {
        return attemptRepository.findByUserIdAndStatus(userId, AttemptStatus.IN_PROGRESS).stream()
                .map(a -> new AttemptInProgressItemResponse(a.getId(), a.getContentId(), a.getRemainingSeconds()))
                .toList();
    }

    public AttemptResultResponse submitAttempt(UUID attemptId, UUID userId) {
        Attempt attempt = findOwnedAttemptForUpdate(attemptId, userId);

        if ((attempt.getStatus() == AttemptStatus.SUBMITTED
                        || attempt.getStatus() == AttemptStatus.FINALIZED
                        || attempt.getStatus() == AttemptStatus.PENDING_REVIEW)
                && attempt.getResult() != null) {
            return toResultResponse(attempt.getResult());
        }

        ensureInProgress(attempt);

        List<AttemptAnswer> answers = attempt.getAnswers().stream()
                .sorted(Comparator.comparing(AttemptAnswer::getBlockOrderIndex, Comparator.nullsLast(Integer::compareTo)))
                .toList();

        BigDecimal maxScore = answers.stream()
                .map(a -> BigDecimal.valueOf(resolveMaxPoints(a)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal score = BigDecimal.ZERO;
        int correctAnswers = 0;
        int pendingManualReviews = 0;

        for (AttemptAnswer answer : answers) {
            ContentBlockSnapshot snapshot = snapshotFromAnswer(attempt, answer);
            GradingDecision decision = gradingEngine.grade(snapshot, answer.getUserAnswer());
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
            if (Boolean.TRUE.equals(decision.correct())) {
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

        attemptAnswerRepository.saveAll(answers);

        AssessmentResult result = resolveOrCreateResult(attempt);
        result.setScore(score);
        result.setMaxScore(maxScore);
        result.setCorrectAnswers(correctAnswers);
        result.setTotalQuestions(answers.size());
        result.setPassed(computePassed(score, maxScore));
        result.setCompletedAt(Instant.now());
        result.setResultStatus(pendingManualReviews > 0 ? ResultStatus.PROVISIONAL : ResultStatus.FINAL);

        attemptRepository.save(attempt);
        result = saveResultIdempotently(attempt, result);

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
        List<AttemptReviewAnswerResponse> answers = attempt.getAnswers().stream()
                .sorted(Comparator.comparing(AttemptAnswer::getBlockOrderIndex, Comparator.nullsLast(Integer::compareTo)))
                .map(answer -> new AttemptReviewAnswerResponse(
                        answer.getContentBlockId(),
                        answer.getKindSnapshot(),
                        answer.getBlockOrderIndex(),
                        resolveMaxPoints(answer),
                        answer.getPayloadSnapshot(),
                        answer.getUserAnswer(),
                        answer.getCorrect(),
                        answer.getAwardedPoints(),
                        answer.getGradingStatus(),
                        answer.getGradingSource(),
                        answer.getConfidence(),
                        answer.getExplanation(),
                        answer.getAiModel()))
                .toList();

        return new AttemptReviewResponse(
                result.getAttempt().getId(),
                result.getAttempt().getContentId(),
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
        return attemptRepository.findAllByUserIdOrderByStartedAtDesc(userId).stream()
                .map(this::toAttemptSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AttemptSummaryResponse> getAllAttempts() {
        return attemptRepository.findAll(Sort.by(Sort.Direction.DESC, "startedAt")).stream()
                .map(this::toAttemptSummary)
                .toList();
    }

    public AttemptResultResponse refreshResultAfterManualReview(UUID attemptId) {
        Attempt attempt = attemptRepository.findByIdForUpdate(attemptId)
                .orElseThrow(() -> new NotFoundException(1404, "Attempt not found: " + attemptId));

        BigDecimal score = BigDecimal.ZERO;
        int correctAnswers = 0;
        int pendingManualReviews = 0;

        for (AttemptAnswer answer : attempt.getAnswers()) {
            if (answer.getGradingStatus() == GradingStatus.MANUAL_REVIEW
                    || answer.getGradingStatus() == GradingStatus.PENDING) {
                pendingManualReviews++;
                continue;
            }
            score = score.add(answer.getAwardedPoints());
            if (Boolean.TRUE.equals(answer.getCorrect())) {
                correctAnswers++;
            }
        }

        BigDecimal maxScore = attempt.getAnswers().stream()
                .map(a -> BigDecimal.valueOf(resolveMaxPoints(a)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        AssessmentResult result = resolveOrCreateResult(attempt);
        result.setScore(score);
        result.setMaxScore(maxScore);
        result.setCorrectAnswers(correctAnswers);
        result.setTotalQuestions(attempt.getAnswers().size());
        result.setPassed(computePassed(score, maxScore));
        result.setCompletedAt(Instant.now());
        result.setResultStatus(pendingManualReviews > 0 ? ResultStatus.PROVISIONAL : ResultStatus.FINAL);

        if (pendingManualReviews > 0) {
            attempt.setStatus(AttemptStatus.PENDING_REVIEW);
        } else {
            attempt.setStatus(AttemptStatus.FINALIZED);
            attempt.setSubmittedAt(result.getCompletedAt());
        }

        attemptRepository.save(attempt);
        result = saveResultIdempotently(attempt, result);
        return toResultResponse(result);
    }

    private ContentBlockSnapshot snapshotFromAnswer(Attempt attempt, AttemptAnswer answer) {
        return new ContentBlockSnapshot(
                answer.getContentBlockId(),
                attempt.getContentId(),
                answer.getKindSnapshot(),
                answer.getPayloadSnapshot(),
                answer.getMaxScoreSnapshot(),
                answer.getBlockOrderIndex(),
                Boolean.TRUE);
    }

    private int resolveMaxPoints(AttemptAnswer answer) {
        return answer.getMaxScoreSnapshot() == null || answer.getMaxScoreSnapshot() < 1
                ? 1
                : answer.getMaxScoreSnapshot();
    }

    private boolean computePassed(BigDecimal score, BigDecimal maxScore) {
        if (maxScore == null || maxScore.signum() <= 0) {
            return Boolean.TRUE;
        }
        BigDecimal threshold = maxScore.multiply(PASS_RATIO).setScale(2, RoundingMode.HALF_UP);
        return score.compareTo(threshold) >= 0;
    }

    private Attempt findOwnedAttempt(UUID attemptId, UUID userId) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new NotFoundException(1404, "Attempt not found: " + attemptId));
        if (!attempt.getUserId().equals(userId)) {
            throw new NotFoundException(1404, "Attempt not found for current user.");
        }
        return attempt;
    }

    private Attempt findOwnedAttemptForUpdate(UUID attemptId, UUID userId) {
        Attempt attempt = attemptRepository.findByIdForUpdate(attemptId)
                .orElseThrow(() -> new NotFoundException(1404, "Attempt not found: " + attemptId));
        if (!attempt.getUserId().equals(userId)) {
            throw new NotFoundException(1404, "Attempt not found for current user.");
        }
        return attempt;
    }

    private AssessmentResult resolveOrCreateResult(Attempt attempt) {
        AssessmentResult result = attempt.getResult();
        if (result != null) {
            return result;
        }
        return assessmentResultRepository
                .findByAttempt_Id(attempt.getId())
                .map(existing -> {
                    attempt.setResult(existing);
                    return existing;
                })
                .orElseGet(() -> {
                    AssessmentResult created = new AssessmentResult();
                    created.setAttempt(attempt);
                    attempt.setResult(created);
                    return created;
                });
    }

    private AssessmentResult saveResultIdempotently(Attempt attempt, AssessmentResult candidate) {
        try {
            return assessmentResultRepository.saveAndFlush(candidate);
        } catch (DataIntegrityViolationException ex) {
            AssessmentResult existing = assessmentResultRepository
                    .findByAttempt_Id(attempt.getId())
                    .orElseThrow(() -> ex);
            existing.setScore(candidate.getScore());
            existing.setMaxScore(candidate.getMaxScore());
            existing.setCorrectAnswers(candidate.getCorrectAnswers());
            existing.setTotalQuestions(candidate.getTotalQuestions());
            existing.setPassed(candidate.getPassed());
            existing.setCompletedAt(candidate.getCompletedAt());
            existing.setResultStatus(candidate.getResultStatus());
            attempt.setResult(existing);
            return assessmentResultRepository.save(existing);
        }
    }

    private void ensureInProgress(Attempt attempt) {
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new ConflictException(1409, "Attempt is not in progress.");
        }
    }

    private void ensureTimeRemaining(Attempt attempt) {
        if (attempt.getRemainingSeconds() != null && attempt.getRemainingSeconds() <= 0) {
            throw new ConflictException(1409, "Time has expired for this attempt.");
        }
    }

    private AttemptResultResponse toResultResponse(AssessmentResult result) {
        long pendingManualReviews = result.getAttempt().getAnswers().stream()
                .filter(answer -> answer.getGradingStatus() == GradingStatus.MANUAL_REVIEW)
                .count();
        return new AttemptResultResponse(
                result.getAttempt().getId(),
                result.getAttempt().getContentId(),
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
                attempt.getContentId(),
                attempt.getStatus(),
                attempt.getStartedAt(),
                attempt.getSubmittedAt(),
                result == null ? BigDecimal.ZERO : result.getScore(),
                result == null ? null : result.getMaxScore(),
                result != null && Boolean.TRUE.equals(result.getPassed()));
    }

    private String extractPrompt(String payload, String kind) {
        if (payload == null || payload.isBlank()) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            if ("QUIZ_MCQ".equals(kind)) {
                return root.path("question").asText("");
            }
            if ("SHORT_ANSWER".equals(kind)) {
                return root.path("prompt").asText("");
            }
            if ("MATCHING".equals(kind) || "ORDERING".equals(kind) || "FILL_IN_THE_BLANKS".equals(kind)) {
                String p = root.path("prompt").asText("");
                if (!p.isBlank()) {
                    return p;
                }
            }
            return root.path("prompt").asText(root.path("question").asText(""));
        } catch (Exception ex) {
            return "";
        }
    }
}
