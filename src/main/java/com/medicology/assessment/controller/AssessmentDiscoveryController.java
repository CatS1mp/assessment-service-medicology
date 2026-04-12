package com.medicology.assessment.controller;

import com.medicology.assessment.dto.common.ApiResponse;
import com.medicology.assessment.dto.response.StudentAssessmentDetailResponse;
import com.medicology.assessment.service.AssessmentService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sections")
@RequiredArgsConstructor
public class AssessmentDiscoveryController {

    private final AssessmentService assessmentService;

    @GetMapping("/{sectionId}/assessment")
    public ResponseEntity<ApiResponse<StudentAssessmentDetailResponse>> getActiveAssessment(
            @PathVariable UUID sectionId,
            @RequestParam(required = false) UUID lessonId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Assessment discovery retrieved successfully.",
                assessmentService.findActiveAssessment(sectionId, lessonId)));
    }
}
