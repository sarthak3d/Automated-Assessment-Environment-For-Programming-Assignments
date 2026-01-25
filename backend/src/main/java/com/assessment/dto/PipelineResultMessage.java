package com.assessment.dto;

import java.time.Instant;
import java.util.UUID;

public record PipelineResultMessage(
    UUID submissionId,
    Long projectId,
    Long pipelineId,
    String status,
    Instant receivedAt
) {}
