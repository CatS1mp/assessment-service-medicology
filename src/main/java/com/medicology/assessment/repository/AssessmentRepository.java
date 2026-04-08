package com.medicology.assessment.repository;

import com.medicology.assessment.entity.Assessment;
import com.medicology.assessment.entity.AssessmentStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {
    Optional<Assessment> findFirstBySectionIdAndLessonIdAndStatusAndActiveTrueOrderByUpdatedAtDesc(
            UUID sectionId,
            UUID lessonId,
            AssessmentStatus status);

    Optional<Assessment> findFirstBySectionIdAndLessonIdIsNullAndStatusAndActiveTrueOrderByUpdatedAtDesc(
            UUID sectionId,
            AssessmentStatus status);
}
