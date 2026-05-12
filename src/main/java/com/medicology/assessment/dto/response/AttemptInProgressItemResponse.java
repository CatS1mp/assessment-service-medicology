package com.medicology.assessment.dto.response;

import java.util.UUID;

public record AttemptInProgressItemResponse(UUID attemptId, UUID contentId, Integer remainingSeconds) {}
