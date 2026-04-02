package com.medicology.assessment.repository;

import com.medicology.assessment.entity.Assessment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {
}
