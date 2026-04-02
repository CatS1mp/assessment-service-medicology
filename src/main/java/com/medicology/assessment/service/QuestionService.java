package com.medicology.assessment.service;

import com.medicology.assessment.dto.request.QuestionOptionRequest;
import com.medicology.assessment.dto.request.QuestionRequest;
import com.medicology.assessment.dto.response.QuestionOptionResponse;
import com.medicology.assessment.dto.response.QuestionResponse;
import com.medicology.assessment.entity.Assessment;
import com.medicology.assessment.entity.Question;
import com.medicology.assessment.entity.QuestionOption;
import com.medicology.assessment.entity.QuestionType;
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
        if (request.type() != QuestionType.SINGLE_CHOICE) {
            throw new BadRequestException(1400, "Only SINGLE_CHOICE questions are supported in the foundation phase.");
        }

        long correctOptions = request.options()
                .stream()
                .filter(QuestionOptionRequest::correct)
                .count();

        if (correctOptions != 1) {
            throw new BadRequestException(1400, "A SINGLE_CHOICE question must contain exactly one correct option.");
        }
    }

    private void applyQuestionRequest(Question question, QuestionRequest request) {
        question.setContent(request.content().trim());
        question.setExplanation(request.explanation());
        question.setType(request.type());
        question.setDisplayOrder(request.displayOrder());
        question.setPoints(request.points());
        question.setActive(request.active() == null ? Boolean.TRUE : request.active());

        question.clearOptions();
        request.options().stream()
                .sorted((left, right) -> Integer.compare(left.displayOrder(), right.displayOrder()))
                .forEach(optionRequest -> question.addOption(toOptionEntity(optionRequest)));
    }

    private QuestionOption toOptionEntity(QuestionOptionRequest request) {
        QuestionOption option = new QuestionOption();
        option.setContent(request.content().trim());
        option.setCorrect(request.correct());
        option.setDisplayOrder(request.displayOrder());
        return option;
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
                question.getOptions().stream().map(this::toOptionResponse).toList());
    }

    private QuestionOptionResponse toOptionResponse(QuestionOption option) {
        return new QuestionOptionResponse(
                option.getId(),
                option.getContent(),
                option.getCorrect(),
                option.getDisplayOrder());
    }
}
