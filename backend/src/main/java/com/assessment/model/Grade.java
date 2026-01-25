package com.assessment.model;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "grades", indexes = {
    @Index(name = "idx_grades_submission", columnList = "submission_id", unique = true),
    @Index(name = "idx_grades_student_assignment", columnList = "student_id, assignment_id")
})
@EntityListeners(AuditingEntityListener.class)
public class Grade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false, unique = true)
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @Column(nullable = false)
    private Double totalScore;

    @Column(nullable = false)
    private Double maxPossibleScore;

    @Column(nullable = false)
    private Double percentageScore;

    @Column(length = 10)
    private String letterGrade;

    @Column(nullable = false)
    private boolean autoGraded = true;

    @Column(columnDefinition = "TEXT")
    private String scoreBreakdown;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "graded_by")
    private User gradedBy;

    @Column
    private Instant manuallyGradedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    public Grade() {}

    public void calculateLetterGrade() {
        if (percentageScore >= 90) { letterGrade = "A"; }
        else if (percentageScore >= 80) { letterGrade = "B"; }
        else if (percentageScore >= 70) { letterGrade = "C"; }
        else if (percentageScore >= 60) { letterGrade = "D"; }
        else { letterGrade = "F"; }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Submission getSubmission() { return submission; }
    public void setSubmission(Submission submission) { this.submission = submission; }
    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }
    public Assignment getAssignment() { return assignment; }
    public void setAssignment(Assignment assignment) { this.assignment = assignment; }
    public Double getTotalScore() { return totalScore; }
    public void setTotalScore(Double totalScore) { this.totalScore = totalScore; }
    public Double getMaxPossibleScore() { return maxPossibleScore; }
    public void setMaxPossibleScore(Double maxPossibleScore) { this.maxPossibleScore = maxPossibleScore; }
    public Double getPercentageScore() { return percentageScore; }
    public void setPercentageScore(Double percentageScore) { this.percentageScore = percentageScore; }
    public String getLetterGrade() { return letterGrade; }
    public void setLetterGrade(String letterGrade) { this.letterGrade = letterGrade; }
    public boolean isAutoGraded() { return autoGraded; }
    public void setAutoGraded(boolean autoGraded) { this.autoGraded = autoGraded; }
    public String getScoreBreakdown() { return scoreBreakdown; }
    public void setScoreBreakdown(String scoreBreakdown) { this.scoreBreakdown = scoreBreakdown; }
    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }
    public User getGradedBy() { return gradedBy; }
    public void setGradedBy(User gradedBy) { this.gradedBy = gradedBy; }
    public Instant getManuallyGradedAt() { return manuallyGradedAt; }
    public void setManuallyGradedAt(Instant manuallyGradedAt) { this.manuallyGradedAt = manuallyGradedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
