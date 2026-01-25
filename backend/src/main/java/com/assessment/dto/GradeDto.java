package com.assessment.dto;

import com.assessment.model.Grade;

import java.time.Instant;
import java.util.UUID;

public record GradeDto(
    UUID id,
    UUID submissionId,
    UUID studentId,
    UUID assignmentId,
    Double totalScore,
    Double maxPossibleScore,
    Double percentageScore,
    String letterGrade,
    boolean autoGraded,
    String scoreBreakdown,
    String feedback,
    Instant manuallyGradedAt,
    Instant createdAt,
    Instant updatedAt
) {
    public static GradeDto fromEntity(Grade grade) {
        UUID submissionId = grade.getSubmission() != null ? grade.getSubmission().getId() : null;
        UUID studentId = grade.getStudent() != null ? grade.getStudent().getId() : null;
        UUID assignmentId = grade.getAssignment() != null ? grade.getAssignment().getId() : null;

        return new GradeDto(
            grade.getId(),
            submissionId,
            studentId,
            assignmentId,
            grade.getTotalScore(),
            grade.getMaxPossibleScore(),
            grade.getPercentageScore(),
            grade.getLetterGrade(),
            grade.isAutoGraded(),
            grade.getScoreBreakdown(),
            grade.getFeedback(),
            grade.getManuallyGradedAt(),
            grade.getCreatedAt(),
            grade.getUpdatedAt()
        );
    }
}
