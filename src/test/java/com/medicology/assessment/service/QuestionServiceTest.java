package com.medicology.assessment.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.medicology.assessment.dto.request.QuestionRequest;
import com.medicology.assessment.entity.QuestionType;
import com.medicology.assessment.exception.BadRequestException;
import com.medicology.assessment.repository.AssessmentRepository;
import com.medicology.assessment.repository.QuestionRepository;
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
    void createQuestion_rejectsBlankPayload() {
        QuestionRequest request = new QuestionRequest(
                "Which answer is correct?",
                null,
                QuestionType.SINGLE_CHOICE,
                1,
                5,
                true,
                " ",
                "{\"correct\":\"A\"}");

        assertThatThrownBy(() -> questionService.createQuestion(UUID.randomUUID(), request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("payload");
    }
}
