package com.assessment.model;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "submissions", indexes = {
    @Index(name = "idx_submissions_student", columnList = "student_id"),
    @Index(name = "idx_submissions_assignment", columnList = "assignment_id"),
    @Index(name = "idx_submissions_pipeline", columnList = "pipelineId"),
    @Index(name = "idx_submissions_status", columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @Column(nullable = false)
    private Integer attemptNumber;

    @Column(length = 100)
    private String commitSha;

    private Long pipelineId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.PENDING;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TestResult> testResults = new ArrayList<>();

    @OneToOne(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    private Grade grade;

    @Column(columnDefinition = "TEXT")
    private String pipelineLogs;

    @Column
    private Instant pipelineStartedAt;

    @Column
    private Instant pipelineFinishedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant submittedAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    public Submission() {}

    public void addTestResult(TestResult testResult) {
        testResults.add(testResult);
        testResult.setSubmission(this);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }
    public Assignment getAssignment() { return assignment; }
    public void setAssignment(Assignment assignment) { this.assignment = assignment; }
    public Integer getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(Integer attemptNumber) { this.attemptNumber = attemptNumber; }
    public String getCommitSha() { return commitSha; }
    public void setCommitSha(String commitSha) { this.commitSha = commitSha; }
    public Long getPipelineId() { return pipelineId; }
    public void setPipelineId(Long pipelineId) { this.pipelineId = pipelineId; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public List<TestResult> getTestResults() { return testResults; }
    public void setTestResults(List<TestResult> testResults) { this.testResults = testResults; }
    public Grade getGrade() { return grade; }
    public void setGrade(Grade grade) { this.grade = grade; }
    public String getPipelineLogs() { return pipelineLogs; }
    public void setPipelineLogs(String pipelineLogs) { this.pipelineLogs = pipelineLogs; }
    public Instant getPipelineStartedAt() { return pipelineStartedAt; }
    public void setPipelineStartedAt(Instant pipelineStartedAt) { this.pipelineStartedAt = pipelineStartedAt; }
    public Instant getPipelineFinishedAt() { return pipelineFinishedAt; }
    public void setPipelineFinishedAt(Instant pipelineFinishedAt) { this.pipelineFinishedAt = pipelineFinishedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public static SubmissionBuilder builder() { return new SubmissionBuilder(); }

    public static class SubmissionBuilder {
        private User student;
        private Assignment assignment;
        private Integer attemptNumber;
        private Status status = Status.PENDING;

        public SubmissionBuilder student(User student) { this.student = student; return this; }
        public SubmissionBuilder assignment(Assignment assignment) { this.assignment = assignment; return this; }
        public SubmissionBuilder attemptNumber(Integer attemptNumber) { this.attemptNumber = attemptNumber; return this; }
        public SubmissionBuilder status(Status status) { this.status = status; return this; }

        public Submission build() {
            Submission s = new Submission();
            s.student = this.student;
            s.assignment = this.assignment;
            s.attemptNumber = this.attemptNumber;
            s.status = this.status;
            return s;
        }
    }

    public enum Status {
        PENDING, QUEUED, RUNNING, PASSED, FAILED, ERROR, CANCELLED, TIMEOUT
    }
}
