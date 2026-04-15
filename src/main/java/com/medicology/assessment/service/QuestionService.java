package com.medicology.assessment.service;

import com.medicology.assessment.dto.request.QuestionRequest;
import com.medicology.assessment.dto.response.QuestionResponse;
import com.medicology.assessment.entity.Assessment;
import com.medicology.assessment.entity.Question;
import com.medicology.assessment.exception.BadRequestException;
import com.medicology.assessment.exception.NotFoundException;
import com.medicology.assessment.repository.AssessmentRepository;
import com.medicology.assessment.repository.QuestionRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class QuestionService {

    private final AssessmentRepository assessmentRepository;
    private final QuestionRepository questionRepository;

    @Transactional(readOnly = true)
    public List<QuestionResponse> listQuestions(UUID assessmentId) {
        findAssessment(assessmentId);
        return questionRepository.findAllByAssessment_IdOrderByDisplayOrder(assessmentId)
                .stream()
                .map(this::toQuestionResponse)
                .toList();
    }

    public QuestionResponse createQuestion(UUID assessmentId, QuestionRequest request) {
        validateQuestionRequest(request);
        Assessment assessment = findAssessment(assessmentId);

        Question question = new Question();
        applyQuestionRequest(question, request);
        question.setAssessment(assessment);

        return toQuestionResponse(questionRepository.save(question));
    }

    public QuestionResponse updateQuestion(UUID questionId, QuestionRequest request) {
        validateQuestionRequest(request);
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new NotFoundException(1404, "Question not found: " + questionId));

        applyQuestionRequest(question, request);
        return toQuestionResponse(questionRepository.save(question));
    }

    public void deleteQuestion(UUID questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new NotFoundException(1404, "Question not found: " + questionId));
        questionRepository.delete(question);
    }

    private Assessment findAssessment(UUID assessmentId) {
        return assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new NotFoundException(1404, "Assessment not found: " + assessmentId));
    }

    private void validateQuestionRequest(QuestionRequest request) {
        if (request.payload().isBlank()) {
            throw new BadRequestException(1400, "payload must not be blank.");
        }
        if (request.answerKey().isBlank()) {
            throw new BadRequestException(1400, "answerKey must not be blank.");
        }
    }

    private void applyQuestionRequest(Question question, QuestionRequest request) {
        question.setContent(request.content().trim());
        question.setExplanation(request.explanation());
        question.setType(request.type());
        question.setDisplayOrder(request.displayOrder());
        question.setPoints(request.points());
        question.setActive(request.active() == null ? Boolean.TRUE : request.active());
        question.setPayload(request.payload());
        question.setAnswerKey(request.answerKey());
        if (question.getId() == null) {
            question.setVersion(1);
        } else {
            question.setVersion(question.getVersion() == null ? 1 : question.getVersion() + 1);
        }
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
                question.getPayload(),
                question.getAnswerKey(),
                question.getVersion());
    }
}
