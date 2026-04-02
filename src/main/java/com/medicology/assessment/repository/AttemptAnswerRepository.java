package com.medicology.assessment.repository;

import com.medicology.assessment.entity.AttemptAnswer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswer, UUID> {

    Optional<AttemptAnswer> findByAttempt_IdAndQuestion_Id(UUID attemptId, UUID questionId);
}
