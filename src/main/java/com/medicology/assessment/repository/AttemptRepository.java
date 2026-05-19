package com.medicology.assessment.repository;

import com.medicology.assessment.entity.Attempt;
import com.medicology.assessment.entity.AttemptStatus;
import com.medicology.assessment.entity.ResultStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttemptRepository extends JpaRepository<Attempt, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Attempt a where a.id = :attemptId")
    Optional<Attempt> findByIdForUpdate(UUID attemptId);

    Optional<Attempt> findTopByContentIdAndUserIdAndStatusOrderByStartedAtDesc(
            UUID contentId, UUID userId, AttemptStatus status);

    List<Attempt> findAllByUserIdOrderByStartedAtDesc(UUID userId);

    List<Attempt> findByUserIdAndStatus(UUID userId, AttemptStatus status);

    java.util.Optional<Attempt> findTopByContentIdAndUserIdAndSubmittedAtIsNotNullOrderBySubmittedAtDesc(
            UUID contentId, UUID userId);

    @Query(
            "select distinct a from Attempt a join fetch a.result r where a.userId = :userId and a.status = :status and r.resultStatus = :resultStatus")
    List<Attempt> findFinalizedCompletionsForUser(
            @Param("userId") UUID userId,
            @Param("status") AttemptStatus status,
            @Param("resultStatus") ResultStatus resultStatus);
}
