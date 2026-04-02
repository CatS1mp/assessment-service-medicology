package com.medicology.assessment.repository;

import com.medicology.assessment.entity.AssessmentResult;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentResultRepository extends JpaRepository<AssessmentResult, UUID> {

    Optional<AssessmentResult> findByAttempt_Id(UUID attemptId);
}
