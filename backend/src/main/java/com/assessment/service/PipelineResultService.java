package com.assessment.service;

import com.assessment.config.RabbitMQConfig;
import com.assessment.dto.PipelineResultMessage;
import com.assessment.model.*;
import com.assessment.repository.SubmissionRepository;
import com.assessment.repository.TestModuleRepository;
import com.assessment.repository.TestResultRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Job;
import org.gitlab4j.api.models.Pipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PipelineResultService {

    private static final Logger log = LoggerFactory.getLogger(PipelineResultService.class);

    private final SubmissionRepository submissionRepository;
    private final TestResultRepository testResultRepository;
    private final TestModuleRepository testModuleRepository;
    private final GitLabService gitLabService;
    private final GradingService gradingService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public PipelineResultService(SubmissionRepository submissionRepository, TestResultRepository testResultRepository,
                                 TestModuleRepository testModuleRepository, GitLabService gitLabService,
                                 GradingService gradingService, RabbitTemplate rabbitTemplate,
                                 ObjectMapper objectMapper) {
        this.submissionRepository = submissionRepository;
        this.testResultRepository = testResultRepository;
        this.testModuleRepository = testModuleRepository;
        this.gitLabService = gitLabService;
        this.gradingService = gradingService;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void processPipelineWebhook(Long projectId, Long pipelineId, String status) {
        log.info("Received pipeline webhook: project={}, pipeline={}, status={}", 
            projectId, pipelineId, status);

        submissionRepository.findByPipelineId(pipelineId).ifPresent(submission -> {
            PipelineResultMessage message = new PipelineResultMessage(
                submission.getId(),
                projectId,
                pipelineId,
                status,
                Instant.now()
            );

            rabbitTemplate.convertAndSend(
                RabbitMQConfig.PIPELINE_RESULTS_EXCHANGE,
                RabbitMQConfig.PIPELINE_RESULTS_ROUTING_KEY,
                message
            );
        });
    }

    @RabbitListener(queues = RabbitMQConfig.PIPELINE_RESULTS_QUEUE)
    @Transactional
    public void handlePipelineResult(PipelineResultMessage message) {
        log.info("Processing pipeline result: submission={}, status={}", 
            message.submissionId(), message.status());

        try {
            Submission submission = submissionRepository.findById(message.submissionId())
                .orElseThrow(() -> new IllegalArgumentException("Submission not found"));

            updateSubmissionStatus(submission, message.status());

            if (isPipelineComplete(message.status())) {
                fetchAndProcessJobResults(submission, message.projectId(), message.pipelineId());

                if (submission.getStatus() == Submission.Status.PASSED ||
                    submission.getStatus() == Submission.Status.FAILED) {
                    gradingService.triggerGrading(submission.getId());
                }
            }

        } catch (Exception e) {
            log.error("Failed to process pipeline result for submission {}: {}",
                message.submissionId(), e.getMessage(), e);
        }
    }

    @Transactional
    public void fetchAndProcessJobResults(Submission submission, Long projectId, Long pipelineId) {
        try {
            List<Job> jobs = gitLabService.getPipelineJobs(projectId, pipelineId);

            for (Job job : jobs) {
                processJobResult(submission, projectId, job);
            }

            StringBuilder logs = new StringBuilder();
            for (Job job : jobs) {
                String jobLog = gitLabService.getJobLog(projectId, job.getId());
                logs.append("=== ").append(job.getName()).append(" ===\n");
                logs.append(jobLog).append("\n\n");
            }
            submission.setPipelineLogs(logs.toString());
            submission.setPipelineFinishedAt(Instant.now());

            submissionRepository.save(submission);

        } catch (GitLabApiException e) {
            log.error("Failed to fetch job results for pipeline {}: {}", pipelineId, e.getMessage());
        }
    }

    private void processJobResult(Submission submission, Long projectId, Job job) {
        String jobName = job.getName();

        if (jobName.equals("compile") || jobName.equals("grader") || jobName.equals("error_evaluator")) {
            return;
        }

        Optional<TestModule> moduleOpt = testModuleRepository.findByName(jobName);
        if (moduleOpt.isEmpty()) {
            log.debug("No test module found for job: {}", jobName);
            return;
        }

        TestModule module = moduleOpt.get();
        TestResult result = testResultRepository
            .findBySubmissionIdAndTestModuleId(submission.getId(), module.getId())
            .orElseGet(() -> {
                TestResult newResult = new TestResult();
                newResult.setSubmission(submission);
                newResult.setTestModule(module);
                return newResult;
            });

        result.setJobId(job.getId());
        result.setJobName(jobName);
        result.setStatus(mapJobStatus(job.getStatus().toString()));

        if (job.getStartedAt() != null) {
            result.setStartedAt(job.getStartedAt().toInstant());
        }
        if (job.getFinishedAt() != null) {
            result.setFinishedAt(job.getFinishedAt().toInstant());
        }
        if (result.getStartedAt() != null && result.getFinishedAt() != null) {
            result.setExecutionTimeMs(
                result.getFinishedAt().toEpochMilli() - result.getStartedAt().toEpochMilli()
            );
        }

        try {
            String artifactPath = jobName + "_result.json";
            Optional<String> artifactContent = gitLabService.getFileContent(
                projectId, artifactPath, "main"
            );

            if (artifactContent.isPresent()) {
                parseAndApplyResult(result, module, artifactContent.get());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch artifact for job {}: {}", jobName, e.getMessage());
        }

        testResultRepository.save(result);
    }

    private void parseAndApplyResult(TestResult result, TestModule module, String jsonContent) {
        try {
            JsonNode json = objectMapper.readTree(jsonContent);
            result.setRawOutput(jsonContent);

            switch (module.getOutputType()) {
                case PASS_FAIL:
                    result.setPassResult(json.path("passed").asBoolean(false));
                    result.setNormalizedScore(result.getPassResult() ? 100.0 : 0.0);
                    break;

                case PERCENTAGE:
                    double percentage = json.path("percentage").asDouble(0);
                    result.setPercentageResult(percentage);
                    result.setNormalizedScore(percentage);
                    break;

                case NUMBER_RANGE:
                    double value = json.path("value").asDouble(0);
                    result.setNumericResult(value);
                    result.setNormalizedScore(result.calculateScore());
                    break;

                case TEXT_MATCH:
                    String text = json.path("result").asText("");
                    result.setTextResult(text);
                    result.setNormalizedScore(result.calculateScore());
                    break;

                case SUGGESTION_ONLY:
                    result.setSuggestionText(json.path("suggestion").asText(""));
                    break;
            }

            if (json.has("error")) {
                result.setErrorMessage(json.path("error").asText());
            }

        } catch (JsonProcessingException e) {
            log.warn("Failed to parse result JSON: {}", e.getMessage());
            result.setErrorMessage("Failed to parse result: " + e.getMessage());
        }
    }

    private void updateSubmissionStatus(Submission submission, String pipelineStatus) {
        Submission.Status newStatus = switch (pipelineStatus.toLowerCase()) {
            case "pending", "created" -> Submission.Status.QUEUED;
            case "running" -> Submission.Status.RUNNING;
            case "success" -> Submission.Status.PASSED;
            case "failed" -> Submission.Status.FAILED;
            case "canceled", "cancelled" -> Submission.Status.CANCELLED;
            case "skipped" -> Submission.Status.ERROR;
            default -> submission.getStatus();
        };

        if (submission.getStatus() != newStatus) {
            submission.setStatus(newStatus);
            if (newStatus == Submission.Status.RUNNING && submission.getPipelineStartedAt() == null) {
                submission.setPipelineStartedAt(Instant.now());
            }
            submissionRepository.save(submission);
        }
    }

    private boolean isPipelineComplete(String status) {
        String lower = status.toLowerCase();
        return lower.equals("success") || lower.equals("failed") || 
               lower.equals("canceled") || lower.equals("cancelled") ||
               lower.equals("skipped");
    }

    private TestResult.Status mapJobStatus(String status) {
        return switch (status.toLowerCase()) {
            case "pending", "created" -> TestResult.Status.PENDING;
            case "running" -> TestResult.Status.RUNNING;
            case "success" -> TestResult.Status.PASSED;
            case "failed" -> TestResult.Status.FAILED;
            case "canceled", "cancelled" -> TestResult.Status.SKIPPED;
            default -> TestResult.Status.ERROR;
        };
    }
}
