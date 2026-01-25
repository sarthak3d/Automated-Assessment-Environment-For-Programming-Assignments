package com.assessment.service;

import com.assessment.config.RabbitMQConfig;
import com.assessment.dto.GradeCalculationRequest;
import com.assessment.dto.GradeResult;
import com.assessment.model.*;
import com.assessment.repository.GradeRepository;
import com.assessment.repository.SubmissionRepository;
import com.assessment.repository.TestModuleWeightRepository;
import com.assessment.repository.TestResultRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GradingService {

    private static final Logger log = LoggerFactory.getLogger(GradingService.class);

    private final SubmissionRepository submissionRepository;
    private final TestResultRepository testResultRepository;
    private final TestModuleWeightRepository testModuleWeightRepository;
    private final GradeRepository gradeRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public GradingService(SubmissionRepository submissionRepository, TestResultRepository testResultRepository,
                          TestModuleWeightRepository testModuleWeightRepository, GradeRepository gradeRepository,
                          RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.submissionRepository = submissionRepository;
        this.testResultRepository = testResultRepository;
        this.testModuleWeightRepository = testModuleWeightRepository;
        this.gradeRepository = gradeRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void triggerGrading(UUID submissionId) {
        GradeCalculationRequest request = new GradeCalculationRequest(
            submissionId,
            Instant.now()
        );

        rabbitTemplate.convertAndSend(
            RabbitMQConfig.GRADING_EXCHANGE,
            RabbitMQConfig.GRADING_ROUTING_KEY,
            request
        );

        log.info("Queued grading request for submission: {}", submissionId);
    }

    @RabbitListener(queues = RabbitMQConfig.GRADING_QUEUE)
    @Transactional
    public void processGradingRequest(GradeCalculationRequest request) {
        log.info("Processing grading request for submission: {}", request.submissionId());

        try {
            Submission submission = submissionRepository.findById(request.submissionId())
                .orElseThrow(() -> new IllegalArgumentException("Submission not found"));

            GradeResult result = calculateGrade(submission);
            saveGrade(submission, result);

            log.info("Grading completed for submission {}: score={}, letter={}",
                submission.getId(), result.percentageScore(), result.letterGrade());

        } catch (Exception e) {
            log.error("Failed to process grading for submission {}: {}",
                request.submissionId(), e.getMessage(), e);
        }
    }

    @Transactional
    public GradeResult calculateGrade(Submission submission) {
        Assignment assignment = submission.getAssignment();
        List<TestModuleWeight> weights = testModuleWeightRepository
            .findByAssignmentIdAndEnabledTrue(assignment.getId());
        List<TestResult> results = testResultRepository
            .findGradableResultsBySubmissionId(submission.getId());

        Map<UUID, TestResult> resultsByModule = results.stream()
            .collect(Collectors.toMap(
                r -> r.getTestModule().getId(),
                r -> r,
                (a, b) -> a
            ));

        double totalWeightedScore = 0.0;
        double totalWeight = 0.0;
        Map<String, Double> breakdown = new LinkedHashMap<>();

        for (TestModuleWeight weight : weights) {
            TestModule module = weight.getTestModule();
            if (!module.isUseForGrading()) {
                continue;
            }

            TestResult result = resultsByModule.get(module.getId());
            double score = 0.0;

            if (result != null && result.getNormalizedScore() != null) {
                score = result.getNormalizedScore();
            } else if (result != null) {
                Double calculatedScore = result.calculateScore();
                if (calculatedScore != null) {
                    score = calculatedScore;
                    result.setNormalizedScore(score);
                    testResultRepository.save(result);
                }
            }

            double weightedScore = score * weight.getWeight();
            totalWeightedScore += weightedScore;
            totalWeight += weight.getWeight();

            breakdown.put(module.getDisplayName(), score);
        }

        double percentageScore = totalWeight > 0 ? (totalWeightedScore / totalWeight) : 0.0;
        String letterGrade = calculateLetterGrade(percentageScore);

        return new GradeResult(
            totalWeightedScore,
            totalWeight * 100,
            percentageScore,
            letterGrade,
            breakdown
        );
    }

    @Transactional
    public void saveGrade(Submission submission, GradeResult result) {
        Grade existingGrade = gradeRepository.findBySubmissionId(submission.getId())
            .orElse(null);

        Grade grade;
        if (existingGrade != null) {
            grade = existingGrade;
        } else {
            grade = new Grade();
            grade.setSubmission(submission);
            grade.setStudent(submission.getStudent());
            grade.setAssignment(submission.getAssignment());
        }

        grade.setTotalScore(result.totalScore());
        grade.setMaxPossibleScore(result.maxPossibleScore());
        grade.setPercentageScore(result.percentageScore());
        grade.setLetterGrade(result.letterGrade());
        grade.setAutoGraded(true);

        try {
            grade.setScoreBreakdown(objectMapper.writeValueAsString(result.breakdown()));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize score breakdown", e);
        }

        gradeRepository.save(grade);
        submission.setGrade(grade);
        submissionRepository.save(submission);
    }

    @Transactional
    public Grade manualOverride(UUID gradeId, Double newScore, String feedback, User gradedBy) {
        Grade grade = gradeRepository.findById(gradeId)
            .orElseThrow(() -> new IllegalArgumentException("Grade not found"));

        grade.setPercentageScore(newScore);
        grade.setLetterGrade(calculateLetterGrade(newScore));
        grade.setFeedback(feedback);
        grade.setGradedBy(gradedBy);
        grade.setAutoGraded(false);
        grade.setManuallyGradedAt(Instant.now());

        return gradeRepository.save(grade);
    }

    private String calculateLetterGrade(double percentageScore) {
        if (percentageScore >= 90) return "A";
        if (percentageScore >= 80) return "B";
        if (percentageScore >= 70) return "C";
        if (percentageScore >= 60) return "D";
        return "F";
    }

    public Map<String, Object> getAssignmentStatistics(UUID assignmentId) {
        Map<String, Object> stats = new LinkedHashMap<>();

        Optional<Double> avgScore = gradeRepository.findAverageScoreByAssignment(assignmentId);
        stats.put("averageScore", avgScore.orElse(0.0));

        List<Object[]> distribution = gradeRepository.findGradeDistributionByAssignment(assignmentId);
        Map<String, Long> gradeDistribution = new LinkedHashMap<>();
        for (Object[] row : distribution) {
            gradeDistribution.put((String) row[0], (Long) row[1]);
        }
        stats.put("gradeDistribution", gradeDistribution);

        List<Object[]> moduleAverages = testResultRepository.findAverageScoresPerTestModuleByAssignment(assignmentId);
        Map<String, Double> moduleStats = new LinkedHashMap<>();
        for (Object[] row : moduleAverages) {
            moduleStats.put(row[0].toString(), (Double) row[1]);
        }
        stats.put("testModuleAverages", moduleStats);

        return stats;
    }
}
