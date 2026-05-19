package com.medicology.assessment.controller;

import com.medicology.assessment.dto.common.ApiResponse;
import com.medicology.assessment.dto.response.UserProgressSnapshotResponse;
import com.medicology.assessment.service.UserProgressSnapshotService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assessment/internal")
@RequiredArgsConstructor
public class AssessmentInternalController {

    private final UserProgressSnapshotService userProgressSnapshotService;

    @GetMapping("/users/{userId}/progress-snapshot")
    public ResponseEntity<ApiResponse<UserProgressSnapshotResponse>> getUserProgressSnapshot(
            @PathVariable UUID userId) {
        return ResponseEntity.ok(
                ApiResponse.success("Success", userProgressSnapshotService.buildForUser(userId)));
    }
}
