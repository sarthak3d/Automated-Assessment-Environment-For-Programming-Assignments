package com.assessment.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "courses", indexes = {
    @Index(name = "idx_courses_code", columnList = "code", unique = true),
    @Index(name = "idx_courses_instructor", columnList = "instructor_id")
})
@EntityListeners(AuditingEntityListener.class)
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instructor_id", nullable = false)
    private User instructor;

    @JsonIgnore
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "course_enrollments",
        joinColumns = @JoinColumn(name = "course_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id"),
        indexes = {
            @Index(name = "idx_enrollments_course", columnList = "course_id"),
            @Index(name = "idx_enrollments_user", columnList = "user_id")
        }
    )
    private Set<User> enrolledStudents = new HashSet<>();

    @JsonIgnore
    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Assignment> assignments = new HashSet<>();

    private Long gitlabGroupId;

    @Column(nullable = false, length = 20)
    private String semester;

    @Column(nullable = false)
    private Integer year;

    @Column(nullable = false)
    private boolean active = true;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    public Course() {}

    public void addStudent(User student) {
        enrolledStudents.add(student);
        student.getEnrolledCourses().add(this);
    }

    public void removeStudent(User student) {
        enrolledStudents.remove(student);
        student.getEnrolledCourses().remove(this);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public User getInstructor() { return instructor; }
    public void setInstructor(User instructor) { this.instructor = instructor; }
    public Set<User> getEnrolledStudents() { return enrolledStudents; }
    public void setEnrolledStudents(Set<User> enrolledStudents) { this.enrolledStudents = enrolledStudents; }
    public Set<Assignment> getAssignments() { return assignments; }
    public void setAssignments(Set<Assignment> assignments) { this.assignments = assignments; }
    public Long getGitlabGroupId() { return gitlabGroupId; }
    public void setGitlabGroupId(Long gitlabGroupId) { this.gitlabGroupId = gitlabGroupId; }
    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public static CourseBuilder builder() { return new CourseBuilder(); }

    public static class CourseBuilder {
        private String code;
        private String name;
        private String description;
        private User instructor;
        private String semester;
        private Integer year;
        private boolean active = true;

        public CourseBuilder code(String code) { this.code = code; return this; }
        public CourseBuilder name(String name) { this.name = name; return this; }
        public CourseBuilder description(String description) { this.description = description; return this; }
        public CourseBuilder instructor(User instructor) { this.instructor = instructor; return this; }
        public CourseBuilder semester(String semester) { this.semester = semester; return this; }
        public CourseBuilder year(Integer year) { this.year = year; return this; }
        public CourseBuilder active(boolean active) { this.active = active; return this; }

        public Course build() {
            Course course = new Course();
            course.code = this.code;
            course.name = this.name;
            course.description = this.description;
            course.instructor = this.instructor;
            course.semester = this.semester;
            course.year = this.year;
            course.active = this.active;
            return course;
        }
    }
}
