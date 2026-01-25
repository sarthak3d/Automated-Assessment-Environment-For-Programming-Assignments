package com.assessment.dto;

public record AuthResponse(
    String token,
    String tokenType,
    long expiresIn,
    UserDto user
) {}
