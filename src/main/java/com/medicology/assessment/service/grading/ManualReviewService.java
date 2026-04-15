package com.medicology.assessment.service.grading;

import com.medicology.assessment.dto.request.ManualReviewFinalizeRequest;
import com.medicology.assessment.dto.response.ManualReviewItemResponse;
import com.medicology.assessment.entity.AttemptAnswer;
import com.medicology.assessment.entity.GradingSource;
import com.medicology.assessment.entity.GradingStatus;
import com.medicology.assessment.exception.NotFoundException;
import com.medicology.assessment.repository.AttemptAnswerRepository;
import com.medicology.assessment.service.AttemptService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ManualReviewService {

    private final AttemptAnswerRepository attemptAnswerRepository;
    private final AttemptService attemptService;

    @Transactional(readOnly = true)
    public List<ManualReviewItemResponse> listQueue() {
        return attemptAnswerRepository.findAllByGradingStatusOrderByAnsweredAtAsc(GradingStatus.MANUAL_REVIEW)
                .stream()
                .map(this::toQueueItem)
                .toList();
    }

    public void finalizeReview(UUID attemptAnswerId, UUID reviewerId, ManualReviewFinalizeRequest request) {
        AttemptAnswer answer = attemptAnswerRepository.findById(attemptAnswerId)
                .orElseThrow(() -> new NotFoundException(1404, "Attempt answer not found: " + attemptAnswerId));

        boolean correct = Boolean.TRUE.equals(request.correct());
        BigDecimal awardedPoints = request.awardedPoints();
        if (awardedPoints == null) {
            awardedPoints = correct ? BigDecimal.valueOf(answer.getQuestion().getPoints()) : BigDecimal.ZERO;
        }

        answer.setCorrect(correct);
        answer.setAwardedPoints(awardedPoints);
        answer.setGradingStatus(GradingStatus.FINALIZED);
        answer.setGradingSource(GradingSource.MANUAL);
        answer.setConfidence(null);
        answer.setExplanation(request.explanation());
        answer.setAiModel(null);
        answer.setEvaluatedBy(reviewerId);
        answer.setEvaluatedAt(Instant.now());

        attemptAnswerRepository.save(answer);
        attemptService.refreshResultAfterManualReview(answer.getAttempt().getId());
    }

    private ManualReviewItemResponse toQueueItem(AttemptAnswer answer) {
        return new ManualReviewItemResponse(
                answer.getId(),
                answer.getAttempt().getId(),
                answer.getQuestion().getId(),
                answer.getQuestion().getContent(),
                answer.getUserAnswer(),
                answer.getPayloadSnapshot(),
                answer.getAnswerKeySnapshot(),
                answer.getConfidence(),
                answer.getExplanation(),
                answer.getAnsweredAt());
    }
}
