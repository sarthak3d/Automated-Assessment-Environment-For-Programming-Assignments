package com.assessment.dto;

import java.time.Instant;
import java.util.UUID;

public record GradeCalculationRequest(
    UUID submissionId,
    Instant requestedAt
) {}
