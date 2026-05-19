package com.medicology.assessment.service;

import com.medicology.assessment.entity.AssessmentResult;
import com.medicology.assessment.entity.Attempt;
import com.medicology.assessment.entity.AttemptStatus;
import com.medicology.assessment.entity.ResultStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.experimental.UtilityClass;

@UtilityClass
public class AttemptResultDisplayHelper {

    public static final BigDecimal PASS_RATIO = new BigDecimal("0.5");

    public record DisplayFields(
            Integer scorePercent, String displayOutcome, String mascotKey, BigDecimal passThresholdScore) {}

    public static DisplayFields resolve(AssessmentResult result) {
        if (result == null || result.getAttempt() == null) {
            return new DisplayFields(null, "NEUTRAL", null, null);
        }
        Attempt attempt = result.getAttempt();
        BigDecimal maxScore = result.getMaxScore();
        BigDecimal passThreshold = passThresholdScore(maxScore);
        Integer scorePercent = scorePercent(result.getScore(), maxScore);

        String outcome;
        if (result.getResultStatus() == ResultStatus.PROVISIONAL
                || attempt.getStatus() == AttemptStatus.PENDING_REVIEW) {
            outcome = "GRADING";
        } else if (result.getResultStatus() == ResultStatus.FINAL && Boolean.TRUE.equals(result.getPassed())) {
            outcome = "PASSED";
        } else if (result.getResultStatus() == ResultStatus.FINAL) {
            outcome = "FAILED";
        } else {
            outcome = "NEUTRAL";
        }

        String mascot = scorePercent == null ? null : mascotKey(scorePercent);
        return new DisplayFields(scorePercent, outcome, mascot, passThreshold);
    }

    public static BigDecimal passThresholdScore(BigDecimal maxScore) {
        if (maxScore == null || maxScore.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return maxScore.multiply(PASS_RATIO).setScale(2, RoundingMode.HALF_UP);
    }

    public static Integer scorePercent(BigDecimal score, BigDecimal maxScore) {
        if (score == null || maxScore == null || maxScore.signum() <= 0) {
            return null;
        }
        int percent = score.multiply(BigDecimal.valueOf(100))
                .divide(maxScore, 0, RoundingMode.HALF_UP)
                .intValue();
        return Math.max(0, Math.min(100, percent));
    }

    public static String mascotKey(int scorePercent) {
        if (scorePercent >= 100) {
            return "mascot-15";
        }
        if (scorePercent >= 75) {
            return "mascot-22";
        }
        if (scorePercent >= 25) {
            return "mascot-23";
        }
        return "mascot-21";
    }
}
