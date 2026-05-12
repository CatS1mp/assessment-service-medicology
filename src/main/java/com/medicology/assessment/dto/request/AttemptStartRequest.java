package com.medicology.assessment.dto.request;

/** Body từ FE khi start attempt — FE tự cung cấp thời lượng (lấy từ learning-service trên client). */
public record AttemptStartRequest(Integer estimatedDurationMinutes) {}
