package com.medicology.assessment.service;

import com.medicology.assessment.dto.request.AssessmentRequest;
import com.medicology.assessment.dto.response.AssessmentDetailResponse;
import com.medicology.assessment.dto.response.AssessmentSummaryResponse;
import com.medicology.assessment.dto.response.QuestionOptionResponse;
import com.medicology.assessment.dto.response.QuestionResponse;
import com.medicology.assessment.dto.response.StudentAssessmentDetailResponse;
import com.medicology.assessment.dto.response.StudentQuestionOptionResponse;
import com.medicology.assessment.dto.response.StudentQuestionResponse;
import com.medicology.assessment.entity.Assessment;
import com.medicology.assessment.entity.AssessmentStatus;
import com.medicology.assessment.entity.Question;
import com.medicology.assessment.entity.QuestionOption;
import com.medicology.assessment.exception.NotFoundException;
import com.medicology.assessment.repository.AssessmentRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AssessmentService {

    private final AssessmentRepository assessmentRepository;
    private final LearningEnrollmentClient learningEnrollmentClient;

    @Transactional(readOnly = true)
    public List<AssessmentSummaryResponse> listAssessments() {
        return assessmentRepository.findAll(Sort.by(Sort.Direction.DESC, "updatedAt"))
                .stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AssessmentDetailResponse getAssessment(UUID assessmentId) {
        return toDetailResponse(findAssessment(assessmentId));
    }

    @Transactional(readOnly = true)
    public StudentAssessmentDetailResponse findActiveAssessment(UUID sectionId, UUID lessonId, UUID userId) {
        Assessment assessment = null;
        if (lessonId != null) {
            var lessonAssessment = assessmentRepository
                    .findFirstBySectionIdAndLessonIdAndStatusAndActiveTrueOrderByUpdatedAtDesc(
                            sectionId,
                            lessonId,
                            AssessmentStatus.PUBLISHED);
            if (lessonAssessment.isPresent()) {
                assessment = lessonAssessment.get();
            }
        }
        if (assessment == null) {
            assessment = assessmentRepository
                    .findFirstBySectionIdAndLessonIdIsNullAndStatusAndActiveTrueOrderByUpdatedAtDesc(
                            sectionId,
                            AssessmentStatus.PUBLISHED)
                    .orElse(null);
        }
        if (assessment == null) {
            return null;
        }
        if (userId != null) {
            learningEnrollmentClient.assertCanAccessAssessment(userId, assessment.getSectionId(), assessment.getLessonId());
        }
        return toStudentDetailResponse(assessment);
    }

    public AssessmentDetailResponse createAssessment(AssessmentRequest request) {
        Assessment assessment = new Assessment();
        applyAssessmentRequest(assessment, request);
        return toDetailResponse(assessmentRepository.save(assessment));
    }

    public AssessmentDetailResponse updateAssessment(UUID assessmentId, AssessmentRequest request) {
        Assessment assessment = findAssessment(assessmentId);
        applyAssessmentRequest(assessment, request);
        return toDetailResponse(assessmentRepository.save(assessment));
    }

    public void deleteAssessment(UUID assessmentId) {
        Assessment assessment = findAssessment(assessmentId);
        assessmentRepository.delete(assessment);
    }

    private Assessment findAssessment(UUID assessmentId) {
        return assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new NotFoundException(1404, "Assessment not found: " + assessmentId));
    }

    private void applyAssessmentRequest(Assessment assessment, AssessmentRequest request) {
        assessment.setTitle(request.title().trim());
        assessment.setDescription(request.description());
        assessment.setCourseId(request.courseId());
        assessment.setSectionId(request.sectionId());
        assessment.setLessonId(request.lessonId());
        assessment.setPassScore(request.passScore());
        assessment.setTimeLimitMinutes(request.timeLimitMinutes());
        assessment.setStatus(request.status());
        assessment.setActive(request.active() == null ? Boolean.TRUE : request.active());
    }

    private AssessmentSummaryResponse toSummaryResponse(Assessment assessment) {
        return new AssessmentSummaryResponse(
                assessment.getId(),
                assessment.getTitle(),
                assessment.getCourseId(),
                assessment.getSectionId(),
                assessment.getLessonId(),
                assessment.getPassScore(),
                assessment.getQuestions().size(),
                assessment.getStatus(),
                assessment.getActive(),
                assessment.getUpdatedAt());
    }

    private AssessmentDetailResponse toDetailResponse(Assessment assessment) {
        return new AssessmentDetailResponse(
                assessment.getId(),
                assessment.getTitle(),
                assessment.getDescription(),
                assessment.getCourseId(),
                assessment.getSectionId(),
                assessment.getLessonId(),
                assessment.getPassScore(),
                assessment.getTimeLimitMinutes(),
                assessment.getStatus(),
                assessment.getActive(),
                assessment.getCreatedAt(),
                assessment.getUpdatedAt(),
                assessment.getQuestions().stream().map(this::toQuestionResponse).toList());
    }

    private QuestionResponse toQuestionResponse(Question question) {
        return new QuestionResponse(
                question.getId(),
                question.getContent(),
                question.getExplanation(),
                question.getType(),
                question.getDisplayOrder(),
                question.getPoints(),
                question.getActive(),
                question.getOptions().stream().map(this::toQuestionOptionResponse).toList());
    }

    private QuestionOptionResponse toQuestionOptionResponse(QuestionOption option) {
        return new QuestionOptionResponse(
                option.getId(),
                option.getContent(),
                option.getCorrect(),
                option.getDisplayOrder());
    }

    private StudentAssessmentDetailResponse toStudentDetailResponse(Assessment assessment) {
        return new StudentAssessmentDetailResponse(
                assessment.getId(),
                assessment.getTitle(),
                assessment.getDescription(),
                assessment.getCourseId(),
                assessment.getSectionId(),
                assessment.getLessonId(),
                assessment.getPassScore(),
                assessment.getTimeLimitMinutes(),
                assessment.getStatus(),
                assessment.getActive(),
                assessment.getCreatedAt(),
                assessment.getUpdatedAt(),
                assessment.getQuestions().stream().map(this::toStudentQuestionResponse).toList());
    }

    private StudentQuestionResponse toStudentQuestionResponse(Question question) {
        return new StudentQuestionResponse(
                question.getId(),
                question.getContent(),
                question.getType(),
                question.getDisplayOrder(),
                question.getPoints(),
                question.getActive(),
                question.getOptions().stream().map(this::toStudentQuestionOptionResponse).toList());
    }

    private StudentQuestionOptionResponse toStudentQuestionOptionResponse(QuestionOption option) {
        return new StudentQuestionOptionResponse(
                option.getId(),
                option.getContent(),
                option.getDisplayOrder());
    }
}
