package com.medicology.assessment.repository;

import com.medicology.assessment.entity.QuestionOption;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionOptionRepository extends JpaRepository<QuestionOption, UUID> {

    Optional<QuestionOption> findByIdAndQuestion_Id(UUID optionId, UUID questionId);
}
