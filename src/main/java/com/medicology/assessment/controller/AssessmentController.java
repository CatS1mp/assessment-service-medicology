package com.medicology.assessment.controller;

import com.medicology.assessment.dto.common.ApiResponse;
import com.medicology.assessment.dto.request.AssessmentRequest;
import com.medicology.assessment.dto.response.AssessmentDetailResponse;
import com.medicology.assessment.dto.response.AssessmentSummaryResponse;
import com.medicology.assessment.service.AssessmentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assessments")
@RequiredArgsConstructor
public class AssessmentController {

    private final AssessmentService assessmentService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AssessmentSummaryResponse>>> listAssessments() {
        return ResponseEntity.ok(ApiResponse.success("Assessments retrieved successfully.", assessmentService.listAssessments()));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AssessmentDetailResponse>> createAssessment(
            @Valid @RequestBody AssessmentRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Assessment created successfully.", assessmentService.createAssessment(request)));
    }

    @GetMapping("/{assessmentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AssessmentDetailResponse>> getAssessment(@PathVariable UUID assessmentId) {
        return ResponseEntity.ok(ApiResponse.success("Assessment retrieved successfully.", assessmentService.getAssessment(assessmentId)));
    }

    @PutMapping("/{assessmentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AssessmentDetailResponse>> updateAssessment(
            @PathVariable UUID assessmentId,
            @Valid @RequestBody AssessmentRequest request) {

        return ResponseEntity.ok(ApiResponse.success(
                "Assessment updated successfully.",
                assessmentService.updateAssessment(assessmentId, request)));
    }

    @DeleteMapping("/{assessmentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteAssessment(@PathVariable UUID assessmentId) {
        assessmentService.deleteAssessment(assessmentId);
        return ResponseEntity.ok(ApiResponse.success("Assessment deleted successfully.", null));
    }
}
