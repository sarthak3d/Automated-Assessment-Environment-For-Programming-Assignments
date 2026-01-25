package com.assessment.dto;

import com.assessment.model.Submission;

import java.time.Instant;
import java.util.UUID;

public record SubmissionDto(
    UUID id,
    UUID studentId,
    UUID assignmentId,
    Integer attemptNumber,
    String commitSha,
    Long pipelineId,
    String status,
    String pipelineLogs,
    Instant pipelineStartedAt,
    Instant pipelineFinishedAt,
    Instant submittedAt,
    Instant updatedAt
) {
    public static SubmissionDto fromEntity(Submission submission) {
        UUID studentId = submission.getStudent() != null ? submission.getStudent().getId() : null;
        UUID assignmentId = submission.getAssignment() != null ? submission.getAssignment().getId() : null;
        String status = submission.getStatus() != null ? submission.getStatus().name() : null;

        return new SubmissionDto(
            submission.getId(),
            studentId,
            assignmentId,
            submission.getAttemptNumber(),
            submission.getCommitSha(),
            submission.getPipelineId(),
            status,
            submission.getPipelineLogs(),
            submission.getPipelineStartedAt(),
            submission.getPipelineFinishedAt(),
            submission.getSubmittedAt(),
            submission.getUpdatedAt()
        );
    }
}
