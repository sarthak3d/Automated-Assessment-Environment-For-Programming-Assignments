package com.assessment.model;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "test_results", indexes = {
    @Index(name = "idx_test_results_submission", columnList = "submission_id"),
    @Index(name = "idx_test_results_module", columnList = "test_module_id"),
    @Index(name = "idx_test_results_job", columnList = "jobId")
})
@EntityListeners(AuditingEntityListener.class)
public class TestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_module_id", nullable = false)
    private TestModule testModule;

    private Long jobId;

    @Column(length = 100)
    private String jobName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.PENDING;

    @Column(columnDefinition = "TEXT")
    private String rawOutput;

    @Column
    private Boolean passResult;

    @Column
    private Double numericResult;

    @Column(length = 255)
    private String textResult;

    @Column
    private Double percentageResult;

    @Column
    private Double normalizedScore;

    @Column(columnDefinition = "TEXT")
    private String suggestionText;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column
    private Long executionTimeMs;

    @Column
    private Long memoryUsedBytes;

    @Column
    private Instant startedAt;

    @Column
    private Instant finishedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public TestResult() {}

    public Double calculateScore() {
        if (testModule == null || !testModule.isUseForGrading()) {
            return null;
        }

        return switch (testModule.getOutputType()) {
            case PASS_FAIL -> passResult != null && passResult ? 100.0 : 0.0;
            case PERCENTAGE -> percentageResult != null ? percentageResult : 0.0;
            case NUMBER_RANGE -> calculateRangeScore();
            case TEXT_MATCH -> calculateTextMatchScore();
            case SUGGESTION_ONLY -> null;
        };
    }

    private Double calculateRangeScore() {
        if (numericResult == null || testModule.getMinValue() == null || testModule.getMaxValue() == null) {
            return 0.0;
        }
        double min = testModule.getMinValue();
        double max = testModule.getMaxValue();
        double value = Math.max(min, Math.min(max, numericResult));
        return ((value - min) / (max - min)) * 100.0;
    }

    private Double calculateTextMatchScore() {
        if (textResult == null || testModule.getValidTextOutputs() == null) {
            return 0.0;
        }
        return testModule.getValidTextOutputs().contains(textResult) ? 100.0 : 0.0;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Submission getSubmission() { return submission; }
    public void setSubmission(Submission submission) { this.submission = submission; }
    public TestModule getTestModule() { return testModule; }
    public void setTestModule(TestModule testModule) { this.testModule = testModule; }
    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }
    public String getJobName() { return jobName; }
    public void setJobName(String jobName) { this.jobName = jobName; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getRawOutput() { return rawOutput; }
    public void setRawOutput(String rawOutput) { this.rawOutput = rawOutput; }
    public Boolean getPassResult() { return passResult; }
    public void setPassResult(Boolean passResult) { this.passResult = passResult; }
    public Double getNumericResult() { return numericResult; }
    public void setNumericResult(Double numericResult) { this.numericResult = numericResult; }
    public String getTextResult() { return textResult; }
    public void setTextResult(String textResult) { this.textResult = textResult; }
    public Double getPercentageResult() { return percentageResult; }
    public void setPercentageResult(Double percentageResult) { this.percentageResult = percentageResult; }
    public Double getNormalizedScore() { return normalizedScore; }
    public void setNormalizedScore(Double normalizedScore) { this.normalizedScore = normalizedScore; }
    public String getSuggestionText() { return suggestionText; }
    public void setSuggestionText(String suggestionText) { this.suggestionText = suggestionText; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(Long executionTimeMs) { this.executionTimeMs = executionTimeMs; }
    public Long getMemoryUsedBytes() { return memoryUsedBytes; }
    public void setMemoryUsedBytes(Long memoryUsedBytes) { this.memoryUsedBytes = memoryUsedBytes; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public Instant getCreatedAt() { return createdAt; }

    public enum Status {
        PENDING, RUNNING, PASSED, FAILED, ERROR, SKIPPED, TIMEOUT
    }
}
