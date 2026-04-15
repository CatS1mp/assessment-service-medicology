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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "attempt_answers",
        uniqueConstraints = @UniqueConstraint(name = "uk_attempt_question", columnNames = {"attempt_id", "question_id"}))
@Getter
@Setter
@NoArgsConstructor
public class AttemptAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private Attempt attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String userAnswer;

    @Column(nullable = false)
    private Integer questionVersion;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payloadSnapshot;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answerKeySnapshot;

    @Column(columnDefinition = "TEXT")
    private String optionContentSnapshot;

    @Column(nullable = false)
    private Boolean correct = Boolean.FALSE;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal awardedPoints = BigDecimal.ZERO;

    @Column(nullable = false)
    private Instant answeredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GradingStatus gradingStatus = GradingStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private GradingSource gradingSource;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(length = 128)
    private String aiModel;

    @Column
    private Instant evaluatedAt;

    @Column
    private UUID evaluatedBy;

    @PrePersist
    @PreUpdate
    public void onWrite() {
        this.answeredAt = Instant.now();
    }
}
