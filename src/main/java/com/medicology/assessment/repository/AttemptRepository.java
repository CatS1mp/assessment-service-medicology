package com.medicology.assessment.repository;

import com.medicology.assessment.entity.Attempt;
import com.medicology.assessment.entity.AttemptStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttemptRepository extends JpaRepository<Attempt, UUID> {

    Optional<Attempt> findTopByAssessment_IdAndUserIdAndStatusOrderByStartedAtDesc(
            UUID assessmentId,
            UUID userId,
            AttemptStatus status);

    List<Attempt> findAllByUserIdOrderByStartedAtDesc(UUID userId);
}
