package com.medicology.assessment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "assessment_results")
@Getter
@Setter
@NoArgsConstructor
public class AssessmentResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false, unique = true)
    private Attempt attempt;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal score = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal maxScore = BigDecimal.ZERO;

    @Column(nullable = false)
    private Integer correctAnswers;

    @Column(nullable = false)
    private Integer totalQuestions;

    @Column(nullable = false)
    private Boolean passed;

    @Column(nullable = false)
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ResultStatus resultStatus = ResultStatus.PROVISIONAL;
}
