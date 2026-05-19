package com.medicology.assessment.service;

import com.medicology.assessment.dto.response.UserProgressSnapshotResponse;
import com.medicology.assessment.dto.response.UserProgressSnapshotResponse.ContentOutcomeItem;
import com.medicology.assessment.dto.response.UserProgressSnapshotResponse.InProgressAttemptItem;
import com.medicology.assessment.dto.response.UserProgressSnapshotResponse.RecentGradedAttemptItem;
import com.medicology.assessment.entity.AssessmentResult;
import com.medicology.assessment.entity.Attempt;
import com.medicology.assessment.entity.AttemptStatus;
import com.medicology.assessment.repository.AttemptRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProgressSnapshotService {

    private static final int RECENT_GRADED_LIMIT = 50;

    private final AttemptRepository attemptRepository;

    @Transactional(readOnly = true)
    public UserProgressSnapshotResponse buildForUser(UUID userId) {
        List<Attempt> attempts = attemptRepository.findAllByUserIdOrderByStartedAtDesc(userId);
        Map<UUID, Attempt> latestFinalizedByContent = new HashMap<>();
        List<InProgressAttemptItem> inProgress = new ArrayList<>();
        List<RecentGradedAttemptItem> recentGraded = new ArrayList<>();

        for (Attempt attempt : attempts) {
            if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
                inProgress.add(new InProgressAttemptItem(attempt.getContentId(), attempt.getId()));
                continue;
            }
            if (attempt.getStatus() != AttemptStatus.FINALIZED) {
                continue;
            }
            if (attempt.getSubmittedAt() == null) {
                continue;
            }
            AssessmentResult result = attempt.getResult();
            if (result == null || result.getScore() == null) {
                continue;
            }

            UUID contentId = attempt.getContentId();
            Attempt existing = latestFinalizedByContent.get(contentId);
            if (existing == null || isAfter(attempt, existing)) {
                latestFinalizedByContent.put(contentId, attempt);
            }

            if (result.getMaxScore() != null && result.getMaxScore().signum() > 0) {
                recentGraded.add(new RecentGradedAttemptItem(
                        attempt.getSubmittedAt(), result.getScore(), result.getMaxScore()));
            }
        }

        List<ContentOutcomeItem> outcomes = latestFinalizedByContent.values().stream()
                .map(attempt -> {
                    AssessmentResult result = attempt.getResult();
                    boolean passed = result != null && Boolean.TRUE.equals(result.getPassed());
                    return new ContentOutcomeItem(attempt.getContentId(), passed, attempt.getSubmittedAt());
                })
                .toList();

        recentGraded.sort(Comparator.comparing(RecentGradedAttemptItem::submittedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed());
        if (recentGraded.size() > RECENT_GRADED_LIMIT) {
            recentGraded = recentGraded.subList(0, RECENT_GRADED_LIMIT);
        }

        return new UserProgressSnapshotResponse(outcomes, inProgress, recentGraded);
    }

    private static boolean isAfter(Attempt candidate, Attempt existing) {
        Instant candidateAt = candidate.getSubmittedAt();
        Instant existingAt = existing.getSubmittedAt();
        if (candidateAt == null) {
            return false;
        }
        if (existingAt == null) {
            return true;
        }
        return !candidateAt.isBefore(existingAt);
    }
}
