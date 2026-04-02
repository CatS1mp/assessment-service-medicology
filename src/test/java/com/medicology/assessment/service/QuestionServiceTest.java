package com.medicology.assessment.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.medicology.assessment.dto.request.QuestionOptionRequest;
import com.medicology.assessment.dto.request.QuestionRequest;
import com.medicology.assessment.entity.QuestionType;
import com.medicology.assessment.exception.BadRequestException;
import com.medicology.assessment.repository.AssessmentRepository;
import com.medicology.assessment.repository.QuestionRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuestionServiceTest {

    @Mock
    private AssessmentRepository assessmentRepository;

    @Mock
    private QuestionRepository questionRepository;

    @InjectMocks
    private QuestionService questionService;

    @Test
    void createQuestion_rejectsMultipleCorrectOptions() {
        QuestionRequest request = new QuestionRequest(
                "Which answer is correct?",
                null,
                QuestionType.SINGLE_CHOICE,
                1,
                5,
                true,
                List.of(
                        new QuestionOptionRequest("A", true, 1),
                        new QuestionOptionRequest("B", true, 2)));

        assertThatThrownBy(() -> questionService.createQuestion(UUID.randomUUID(), request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("exactly one correct option");
    }
}
