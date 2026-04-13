package com.medicology.assessment.service;

import java.util.UUID;

public interface LearningEnrollmentClient {

    void assertCanAccessAssessment(UUID userId, UUID sectionId, UUID lessonId);
}
