package com.medicology.assessment.controller;

import com.medicology.assessment.dto.common.ApiResponse;
import com.medicology.assessment.dto.request.ManualReviewFinalizeRequest;
import com.medicology.assessment.dto.response.ManualReviewItemResponse;
import com.medicology.assessment.service.grading.ManualReviewService;
import com.medicology.assessment.utils.SecurityUtils;
import com.medicology.assessment.wrapper.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ManualReviewController {

    private final ManualReviewService manualReviewService;

    @GetMapping("/api/v1/admin/reviews/attempt-answers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ManualReviewItemResponse>>> listQueue() {
        return ResponseEntity.ok(ApiResponse.success(
                "Manual review queue retrieved successfully.",
                manualReviewService.listQueue()));
    }

    @PostMapping("/api/v1/admin/reviews/attempt-answers/{attemptAnswerId}/finalize")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> finalizeReview(
            @PathVariable UUID attemptAnswerId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ManualReviewFinalizeRequest request) {
        manualReviewService.finalizeReview(attemptAnswerId, SecurityUtils.requireUserId(principal), request);
        return ResponseEntity.ok(ApiResponse.success("Manual review finalized successfully.", null));
    }
}
