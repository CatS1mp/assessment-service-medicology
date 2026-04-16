package com.medicology.assessment.repository;

import com.medicology.assessment.entity.Attempt;
import com.medicology.assessment.entity.AttemptStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface AttemptRepository extends JpaRepository<Attempt, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Attempt a where a.id = :attemptId")
    Optional<Attempt> findByIdForUpdate(UUID attemptId);

    Optional<Attempt> findTopByAssessment_IdAndUserIdAndStatusOrderByStartedAtDesc(
            UUID assessmentId,
            UUID userId,
            AttemptStatus status);

    List<Attempt> findAllByUserIdOrderByStartedAtDesc(UUID userId);
}
