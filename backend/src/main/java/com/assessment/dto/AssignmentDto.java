package com.assessment.dto;

import com.assessment.model.Assignment;

import java.time.Instant;
import java.util.UUID;

public record AssignmentDto(
    UUID id,
    UUID courseId,
    String title,
    String description,
    Long gitlabProjectId,
    String gitlabProjectPath,
    String status,
    Instant dueDate,
    Instant publishedAt,
    Integer maxSubmissions,
    boolean allowLateSubmissions,
    Instant createdAt,
    Instant updatedAt
) {
    public static AssignmentDto fromEntity(Assignment assignment) {
        UUID courseId = assignment.getCourse() != null ? assignment.getCourse().getId() : null;
        String status = assignment.getStatus() != null ? assignment.getStatus().name() : null;

        return new AssignmentDto(
            assignment.getId(),
            courseId,
            assignment.getTitle(),
            assignment.getDescription(),
            assignment.getGitlabProjectId(),
            assignment.getGitlabProjectPath(),
            status,
            assignment.getDueDate(),
            assignment.getPublishedAt(),
            assignment.getMaxSubmissions(),
            assignment.isAllowLateSubmissions(),
            assignment.getCreatedAt(),
            assignment.getUpdatedAt()
        );
    }
}
