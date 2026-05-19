package com.medicology.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.medicology.assessment.dto.response.UserProgressSnapshotResponse;
import com.medicology.assessment.entity.AssessmentResult;
import com.medicology.assessment.entity.Attempt;
import com.medicology.assessment.entity.AttemptStatus;
import com.medicology.assessment.repository.AttemptRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserProgressSnapshotServiceTest {

    @Mock
    private AttemptRepository attemptRepository;

    @InjectMocks
    private UserProgressSnapshotService userProgressSnapshotService;

    @Test
    void buildForUser_usesLatestFinalizedAttemptBySubmittedAt_notOlderFailedRetry() {
        UUID userId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();

        Attempt olderFailed = finalizedAttempt(
                contentId,
                Instant.parse("2026-05-19T08:00:00Z"),
                Instant.parse("2026-05-19T08:30:00Z"),
                false);
        Attempt newerPassed = finalizedAttempt(
                contentId,
                Instant.parse("2026-05-19T09:00:00Z"),
                Instant.parse("2026-05-19T09:30:00Z"),
                true);

        when(attemptRepository.findAllByUserIdOrderByStartedAtDesc(userId))
                .thenReturn(List.of(newerPassed, olderFailed));

        UserProgressSnapshotResponse snapshot = userProgressSnapshotService.buildForUser(userId);

        assertThat(snapshot.latestFinalizedByContent()).hasSize(1);
        assertThat(snapshot.latestFinalizedByContent().get(0).contentId()).isEqualTo(contentId);
        assertThat(snapshot.latestFinalizedByContent().get(0).passed()).isTrue();
        assertThat(snapshot.latestFinalizedByContent().get(0).completedAt())
                .isEqualTo(Instant.parse("2026-05-19T09:30:00Z"));
    }

    private static Attempt finalizedAttempt(
            UUID contentId, Instant startedAt, Instant submittedAt, boolean passed) {
        Attempt attempt = new Attempt();
        attempt.setContentId(contentId);
        attempt.setStatus(AttemptStatus.FINALIZED);
        attempt.setStartedAt(startedAt);
        attempt.setSubmittedAt(submittedAt);

        AssessmentResult result = new AssessmentResult();
        result.setScore(BigDecimal.TEN);
        result.setMaxScore(BigDecimal.TEN);
        result.setPassed(passed);
        attempt.setResult(result);
        return attempt;
    }
}
