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
@Table(name = "questions", indexes = {
    @Index(name = "idx_questions_assignment", columnList = "assignment_id")
})
@EntityListeners(AuditingEntityListener.class)
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String markdownContent;

    @Column(length = 500)
    private String markdownFilePath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @Column(nullable = false)
    private Integer languageId;

    @Column(nullable = false, length = 50)
    private String languageName;

    @ElementCollection
    @CollectionTable(name = "question_stdin", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "stdin_input", columnDefinition = "TEXT")
    @OrderColumn(name = "input_order")
    private List<String> stdinInputs = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "question_expected_output", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "expected_output", columnDefinition = "TEXT")
    @OrderColumn(name = "output_order")
    private List<String> expectedOutputs = new ArrayList<>();

    @Column(nullable = false)
    private Integer orderIndex = 0;

    @Column(nullable = false)
    private Integer points = 100;

    @Column(nullable = false)
    private Integer timeoutSeconds = 30;

    @Column(nullable = false)
    private Integer memoryLimitMb = 256;

    @Column(columnDefinition = "TEXT")
    private String starterCode;

    @Column(columnDefinition = "TEXT")
    private String solutionCode;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    public Question() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMarkdownContent() { return markdownContent; }
    public void setMarkdownContent(String markdownContent) { this.markdownContent = markdownContent; }
    public String getMarkdownFilePath() { return markdownFilePath; }
    public void setMarkdownFilePath(String markdownFilePath) { this.markdownFilePath = markdownFilePath; }
    public Assignment getAssignment() { return assignment; }
    public void setAssignment(Assignment assignment) { this.assignment = assignment; }
    public Integer getLanguageId() { return languageId; }
    public void setLanguageId(Integer languageId) { this.languageId = languageId; }
    public String getLanguageName() { return languageName; }
    public void setLanguageName(String languageName) { this.languageName = languageName; }
    public List<String> getStdinInputs() { return stdinInputs; }
    public void setStdinInputs(List<String> stdinInputs) { this.stdinInputs = stdinInputs; }
    public List<String> getExpectedOutputs() { return expectedOutputs; }
    public void setExpectedOutputs(List<String> expectedOutputs) { this.expectedOutputs = expectedOutputs; }
    public Integer getOrderIndex() { return orderIndex; }
    public void setOrderIndex(Integer orderIndex) { this.orderIndex = orderIndex; }
    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }
    public Integer getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Integer timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public Integer getMemoryLimitMb() { return memoryLimitMb; }
    public void setMemoryLimitMb(Integer memoryLimitMb) { this.memoryLimitMb = memoryLimitMb; }
    public String getStarterCode() { return starterCode; }
    public void setStarterCode(String starterCode) { this.starterCode = starterCode; }
    public String getSolutionCode() { return solutionCode; }
    public void setSolutionCode(String solutionCode) { this.solutionCode = solutionCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
