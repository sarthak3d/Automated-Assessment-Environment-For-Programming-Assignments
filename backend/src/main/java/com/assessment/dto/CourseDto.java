package com.assessment.dto;

import com.assessment.model.Course;

import java.time.Instant;
import java.util.UUID;

public record CourseDto(
    UUID id,
    String code,
    String name,
    String description,
    UUID instructorId,
    Long gitlabGroupId,
    String semester,
    Integer year,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
    public static CourseDto fromEntity(Course course) {
        UUID instructorId = course.getInstructor() != null ? course.getInstructor().getId() : null;
        return new CourseDto(
            course.getId(),
            course.getCode(),
            course.getName(),
            course.getDescription(),
            instructorId,
            course.getGitlabGroupId(),
            course.getSemester(),
            course.getYear(),
            course.isActive(),
            course.getCreatedAt(),
            course.getUpdatedAt()
        );
    }
}
