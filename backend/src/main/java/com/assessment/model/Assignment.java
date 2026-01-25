package com.assessment.model;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "assignments", indexes = {
    @Index(name = "idx_assignments_course", columnList = "course_id"),
    @Index(name = "idx_assignments_due_date", columnList = "dueDate")
})
@EntityListeners(AuditingEntityListener.class)
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<Question> questions = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "assignment_test_modules",
        joinColumns = @JoinColumn(name = "assignment_id"),
        inverseJoinColumns = @JoinColumn(name = "test_module_id")
    )
    private Set<TestModule> selectedTestModules = new HashSet<>();

    @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TestModuleWeight> testModuleWeights = new ArrayList<>();

    private Long gitlabProjectId;

    @Column(length = 500)
    private String gitlabProjectPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.DRAFT;

    @Column(nullable = false)
    private Instant dueDate;

    private Instant publishedAt;

    @Column(nullable = false)
    private Integer maxSubmissions = 10;

    @Column(nullable = false)
    private boolean allowLateSubmissions = false;

    @Column(columnDefinition = "TEXT")
    private String ciConfigYaml;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    public Assignment() {}

    public void addQuestion(Question question) {
        questions.add(question);
        question.setAssignment(this);
        question.setOrderIndex(questions.size() - 1);
    }

    public void removeQuestion(Question question) {
        questions.remove(question);
        question.setAssignment(null);
        reorderQuestions();
    }

    private void reorderQuestions() {
        for (int i = 0; i < questions.size(); i++) {
            questions.get(i).setOrderIndex(i);
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }
    public List<Question> getQuestions() { return questions; }
    public void setQuestions(List<Question> questions) { this.questions = questions; }
    public Set<TestModule> getSelectedTestModules() { return selectedTestModules; }
    public void setSelectedTestModules(Set<TestModule> selectedTestModules) { this.selectedTestModules = selectedTestModules; }
    public List<TestModuleWeight> getTestModuleWeights() { return testModuleWeights; }
    public void setTestModuleWeights(List<TestModuleWeight> testModuleWeights) { this.testModuleWeights = testModuleWeights; }
    public Long getGitlabProjectId() { return gitlabProjectId; }
    public void setGitlabProjectId(Long gitlabProjectId) { this.gitlabProjectId = gitlabProjectId; }
    public String getGitlabProjectPath() { return gitlabProjectPath; }
    public void setGitlabProjectPath(String gitlabProjectPath) { this.gitlabProjectPath = gitlabProjectPath; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Instant getDueDate() { return dueDate; }
    public void setDueDate(Instant dueDate) { this.dueDate = dueDate; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public Integer getMaxSubmissions() { return maxSubmissions; }
    public void setMaxSubmissions(Integer maxSubmissions) { this.maxSubmissions = maxSubmissions; }
    public boolean isAllowLateSubmissions() { return allowLateSubmissions; }
    public void setAllowLateSubmissions(boolean allowLateSubmissions) { this.allowLateSubmissions = allowLateSubmissions; }
    public String getCiConfigYaml() { return ciConfigYaml; }
    public void setCiConfigYaml(String ciConfigYaml) { this.ciConfigYaml = ciConfigYaml; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public static AssignmentBuilder builder() { return new AssignmentBuilder(); }

    public static class AssignmentBuilder {
        private String title;
        private String description;
        private Course course;
        private Instant dueDate;
        private Integer maxSubmissions = 10;
        private boolean allowLateSubmissions = false;
        private Status status = Status.DRAFT;

        public AssignmentBuilder title(String title) { this.title = title; return this; }
        public AssignmentBuilder description(String description) { this.description = description; return this; }
        public AssignmentBuilder course(Course course) { this.course = course; return this; }
        public AssignmentBuilder dueDate(Instant dueDate) { this.dueDate = dueDate; return this; }
        public AssignmentBuilder maxSubmissions(Integer maxSubmissions) { this.maxSubmissions = maxSubmissions; return this; }
        public AssignmentBuilder allowLateSubmissions(boolean allowLateSubmissions) { this.allowLateSubmissions = allowLateSubmissions; return this; }
        public AssignmentBuilder status(Status status) { this.status = status; return this; }

        public Assignment build() {
            Assignment a = new Assignment();
            a.title = this.title;
            a.description = this.description;
            a.course = this.course;
            a.dueDate = this.dueDate;
            a.maxSubmissions = this.maxSubmissions;
            a.allowLateSubmissions = this.allowLateSubmissions;
            a.status = this.status;
            return a;
        }
    }

    public enum Status {
        DRAFT, PUBLISHED, CLOSED, ARCHIVED
    }
}
