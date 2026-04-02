package com.medicology.assessment.repository;

import com.medicology.assessment.entity.Question;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

    List<Question> findAllByAssessment_IdOrderByDisplayOrder(UUID assessmentId);

    Optional<Question> findByIdAndAssessment_Id(UUID questionId, UUID assessmentId);
}
