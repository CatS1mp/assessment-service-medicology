package com.medicology.assessment.controller;

import com.medicology.assessment.dto.common.ApiResponse;
import com.medicology.assessment.dto.request.AttemptAnswerRequest;
import com.medicology.assessment.dto.request.AttemptStartRequest;
import com.medicology.assessment.dto.request.AttemptTickRequest;
import com.medicology.assessment.dto.response.AttemptAnswerLookupResponse;
import com.medicology.assessment.dto.response.AttemptAnswerResponse;
import com.medicology.assessment.dto.response.AttemptInProgressItemResponse;
import com.medicology.assessment.dto.response.AttemptResultResponse;
import com.medicology.assessment.dto.response.AttemptReviewResponse;
import com.medicology.assessment.dto.response.AttemptStartResponse;
import com.medicology.assessment.dto.response.AttemptSummaryResponse;
import com.medicology.assessment.dto.response.AttemptTickResponse;
import com.medicology.assessment.service.AttemptService;
import com.medicology.assessment.utils.SecurityUtils;
import com.medicology.assessment.wrapper.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
public class AttemptController {

    private final AttemptService attemptService;

    @PostMapping("/api/v1/contents/{contentId}/attempts")
    public ResponseEntity<ApiResponse<AttemptStartResponse>> startAttempt(
            @PathVariable UUID contentId,
            @RequestBody(required = false) AttemptStartRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Attempt started successfully.",
                        attemptService.startAttempt(contentId, SecurityUtils.requireUserId(principal), request)));
    }

    @PostMapping("/api/v1/attempts/{attemptId}/answers")
    public ResponseEntity<ApiResponse<AttemptAnswerResponse>> saveAnswer(
            @PathVariable UUID attemptId,
            @Valid @RequestBody AttemptAnswerRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.success(
                "Answer saved successfully.",
                attemptService.saveAnswer(attemptId, SecurityUtils.requireUserId(principal), request)));
    }

    @GetMapping("/api/v1/attempts/{attemptId}/blocks/{contentBlockId}/answer")
    public ResponseEntity<ApiResponse<AttemptAnswerLookupResponse>> getAnswer(
            @PathVariable UUID attemptId,
            @PathVariable UUID contentBlockId,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.success(
                "Answer retrieved successfully.",
                attemptService.getAnswer(attemptId, contentBlockId, SecurityUtils.requireUserId(principal))));
    }

    @PostMapping("/api/v1/attempts/{attemptId}/tick")
    public ResponseEntity<ApiResponse<AttemptTickResponse>> tickAttempt(
            @PathVariable UUID attemptId,
            @RequestBody(required = false) AttemptTickRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.success(
                "Attempt time updated.",
                attemptService.tickRemaining(attemptId, SecurityUtils.requireUserId(principal), request)));
    }

    @GetMapping("/api/v1/users/me/in-progress-attempts")
    public ResponseEntity<ApiResponse<List<AttemptInProgressItemResponse>>> getMyInProgressAttempts(
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.success(
                "In-progress attempts retrieved successfully.",
                attemptService.getInProgressAttempts(SecurityUtils.requireUserId(principal))));
    }

    @PostMapping("/api/v1/attempts/{attemptId}/submit")
    public ResponseEntity<ApiResponse<AttemptResultResponse>> submitAttempt(
            @PathVariable UUID attemptId, @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.success(
                "Attempt submitted successfully.",
                attemptService.submitAttempt(attemptId, SecurityUtils.requireUserId(principal))));
    }

    @GetMapping("/api/v1/attempts/{attemptId}/result")
    public ResponseEntity<ApiResponse<AttemptResultResponse>> getResult(
            @PathVariable UUID attemptId, @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.success(
                "Attempt result retrieved successfully.",
                attemptService.getResult(attemptId, SecurityUtils.requireUserId(principal))));
    }

    @GetMapping("/api/v1/attempts/{attemptId}/review")
    public ResponseEntity<ApiResponse<AttemptReviewResponse>> getReview(
            @PathVariable UUID attemptId, @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.success(
                "Attempt review retrieved successfully.",
                attemptService.getReview(attemptId, SecurityUtils.requireUserId(principal))));
    }

    @GetMapping("/api/v1/users/me/attempts")
    public ResponseEntity<ApiResponse<List<AttemptSummaryResponse>>> getMyAttempts(
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.success(
                "Attempts retrieved successfully.",
                attemptService.getMyAttempts(SecurityUtils.requireUserId(principal))));
    }

    @GetMapping("/api/v1/admin/attempts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AttemptSummaryResponse>>> getAllAttempts() {
        return ResponseEntity.ok(ApiResponse.success(
                "All attempts retrieved successfully.",
                attemptService.getAllAttempts()));
    }
}
