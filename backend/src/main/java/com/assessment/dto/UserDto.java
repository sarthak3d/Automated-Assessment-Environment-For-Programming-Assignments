package com.assessment.dto;

import com.assessment.model.User;

import java.time.Instant;
import java.util.UUID;

public record UserDto(
    UUID id,
    String username,
    String email,
    String firstName,
    String lastName,
    String fullName,
    String role,
    boolean active,
    String department,
    String studentId,
    Instant lastLoginAt,
    Instant createdAt
) {
    public static UserDto fromEntity(User user) {
        return new UserDto(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getFullName(),
            user.getRole().name(),
            user.isActive(),
            user.getDepartment(),
            user.getStudentId(),
            user.getLastLoginAt(),
            user.getCreatedAt()
        );
    }
}
