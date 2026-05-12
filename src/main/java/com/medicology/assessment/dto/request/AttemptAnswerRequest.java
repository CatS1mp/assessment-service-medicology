package com.medicology.assessment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Body từ FE khi lưu câu trả lời.
 * Snapshot của content block (kind/payload/maxScore/orderIndex) do FE cung cấp vì assessment-service
 * không gọi learning-service. FE đã có dữ liệu này khi render bài học.
 */
public record AttemptAnswerRequest(
        @NotNull(message = "contentBlockId is required") UUID contentBlockId,
        @NotNull(message = "contentId is required") UUID contentId,
        @NotNull(message = "userAnswer is required") String userAnswer,
        @NotBlank(message = "kind is required") String kind,
        String payload,
        Integer maxScore,
        Integer orderIndex,
        Boolean isGradable) {}
