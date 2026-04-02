package com.medicology.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medicology.assessment.dto.request.AssessmentRequest;
import com.medicology.assessment.dto.response.AssessmentDetailResponse;
import com.medicology.assessment.entity.Assessment;
import com.medicology.assessment.entity.AssessmentStatus;
import com.medicology.assessment.repository.AssessmentRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssessmentServiceTest {

    @Mock
    private AssessmentRepository assessmentRepository;

    @InjectMocks
    private AssessmentService assessmentService;

    @Captor
    private ArgumentCaptor<Assessment> assessmentCaptor;

    @Test
    void createAssessment_persistsBoundaryFields() {
        AssessmentRequest request = new AssessmentRequest(
                "Module 1 Quiz",
                "Cardiology entry assessment",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                70,
                30,
                AssessmentStatus.PUBLISHED,
                true);

        when(assessmentRepository.save(any(Assessment.class))).thenAnswer(invocation -> {
            Assessment assessment = invocation.getArgument(0);
            assessment.setId(UUID.randomUUID());
            return assessment;
        });

        AssessmentDetailResponse response = assessmentService.createAssessment(request);

        verify(assessmentRepository).save(assessmentCaptor.capture());
        Assessment persisted = assessmentCaptor.getValue();

        assertThat(persisted.getTitle()).isEqualTo("Module 1 Quiz");
        assertThat(persisted.getPassScore()).isEqualTo(70);
        assertThat(persisted.getCourseId()).isEqualTo(request.courseId());
        assertThat(response.status()).isEqualTo(AssessmentStatus.PUBLISHED);
        assertThat(response.questions()).isEmpty();
    }
}
