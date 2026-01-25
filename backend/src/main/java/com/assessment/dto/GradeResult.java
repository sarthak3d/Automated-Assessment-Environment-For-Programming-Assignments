package com.assessment.dto;

import java.util.Map;

public record GradeResult(
    Double totalScore,
    Double maxPossibleScore,
    Double percentageScore,
    String letterGrade,
    Map<String, Double> breakdown
) {}
