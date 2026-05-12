package com.medicology.assessment.repository;

import com.medicology.assessment.entity.AttemptAnswer;
import com.medicology.assessment.entity.GradingStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswer, UUID> {

    Optional<AttemptAnswer> findByAttempt_IdAndContentBlockId(UUID attemptId, UUID contentBlockId);

    List<AttemptAnswer> findAllByGradingStatusOrderByAnsweredAtAsc(GradingStatus gradingStatus);
}
