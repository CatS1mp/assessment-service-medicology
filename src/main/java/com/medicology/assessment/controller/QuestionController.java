package com.medicology.assessment.controller;

import com.medicology.assessment.dto.common.ApiResponse;
import com.medicology.assessment.dto.request.QuestionRequest;
import com.medicology.assessment.dto.response.QuestionResponse;
import com.medicology.assessment.service.QuestionService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;

    @GetMapping("/api/v1/assessments/{assessmentId}/questions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<QuestionResponse>>> listQuestions(@PathVariable UUID assessmentId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Questions retrieved successfully.",
                questionService.listQuestions(assessmentId)));
    }

    @PostMapping("/api/v1/assessments/{assessmentId}/questions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<QuestionResponse>> createQuestion(
            @PathVariable UUID assessmentId,
            @Valid @RequestBody QuestionRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Question created successfully.",
                        questionService.createQuestion(assessmentId, request)));
    }

    @PutMapping("/api/v1/questions/{questionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<QuestionResponse>> updateQuestion(
            @PathVariable UUID questionId,
            @Valid @RequestBody QuestionRequest request) {

        return ResponseEntity.ok(ApiResponse.success(
                "Question updated successfully.",
                questionService.updateQuestion(questionId, request)));
    }

    @DeleteMapping("/api/v1/questions/{questionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteQuestion(@PathVariable UUID questionId) {
        questionService.deleteQuestion(questionId);
        return ResponseEntity.ok(ApiResponse.success("Question deleted successfully.", null));
    }
}
