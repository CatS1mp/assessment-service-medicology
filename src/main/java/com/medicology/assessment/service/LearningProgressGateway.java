package com.medicology.assessment.service;

import com.medicology.assessment.dto.request.LearningProgressSyncRequest;

public interface LearningProgressGateway {

    void publishAssessmentResult(LearningProgressSyncRequest request);
}
