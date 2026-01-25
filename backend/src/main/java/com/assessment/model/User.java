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
@Table(name = "users", indexes = {
    @Index(name = "idx_users_username", columnList = "username", unique = true),
    @Index(name = "idx_users_email", columnList = "email", unique = true),
    @Index(name = "idx_users_sso_id", columnList = "ssoId", unique = true)
})
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(unique = true, length = 255)
    private String ssoId;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean active = true;

    @Column(length = 100)
    private String department;

    @Column(length = 50)
    private String studentId;

    private Long gitlabUserId;

    @JsonIgnore
    @Column(length = 255)
    private String passwordHash;

    @JsonIgnore
    @ManyToMany(mappedBy = "enrolledStudents", fetch = FetchType.LAZY)
    private Set<Course> enrolledCourses = new HashSet<>();

    @JsonIgnore
    @OneToMany(mappedBy = "instructor", fetch = FetchType.LAZY)
    private Set<Course> taughtCourses = new HashSet<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    private Instant lastLoginAt;

    public User() {}

    public User(UUID id, String username, String email, String ssoId, String firstName,
                String lastName, Role role, boolean active, String department, String studentId,
                Long gitlabUserId, Set<Course> enrolledCourses, Set<Course> taughtCourses,
                Instant createdAt, Instant updatedAt, Instant lastLoginAt) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.ssoId = ssoId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
        this.active = active;
        this.department = department;
        this.studentId = studentId;
        this.gitlabUserId = gitlabUserId;
        this.enrolledCourses = enrolledCourses != null ? enrolledCourses : new HashSet<>();
        this.taughtCourses = taughtCourses != null ? taughtCourses : new HashSet<>();
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastLoginAt = lastLoginAt;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getSsoId() { return ssoId; }
    public void setSsoId(String ssoId) { this.ssoId = ssoId; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public Long getGitlabUserId() { return gitlabUserId; }
    public void setGitlabUserId(Long gitlabUserId) { this.gitlabUserId = gitlabUserId; }
    public Set<Course> getEnrolledCourses() { return enrolledCourses; }
    public void setEnrolledCourses(Set<Course> enrolledCourses) { this.enrolledCourses = enrolledCourses; }
    public Set<Course> getTaughtCourses() { return taughtCourses; }
    public void setTaughtCourses(Set<Course> taughtCourses) { this.taughtCourses = taughtCourses; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public static UserBuilder builder() { return new UserBuilder(); }

    public static class UserBuilder {
        private UUID id;
        private String username;
        private String email;
        private String ssoId;
        private String firstName;
        private String lastName;
        private Role role;
        private boolean active = true;
        private String department;
        private String studentId;
        private Long gitlabUserId;

        public UserBuilder id(UUID id) { this.id = id; return this; }
        public UserBuilder username(String username) { this.username = username; return this; }
        public UserBuilder email(String email) { this.email = email; return this; }
        public UserBuilder ssoId(String ssoId) { this.ssoId = ssoId; return this; }
        public UserBuilder firstName(String firstName) { this.firstName = firstName; return this; }
        public UserBuilder lastName(String lastName) { this.lastName = lastName; return this; }
        public UserBuilder role(Role role) { this.role = role; return this; }
        public UserBuilder active(boolean active) { this.active = active; return this; }
        public UserBuilder department(String department) { this.department = department; return this; }
        public UserBuilder studentId(String studentId) { this.studentId = studentId; return this; }
        public UserBuilder gitlabUserId(Long gitlabUserId) { this.gitlabUserId = gitlabUserId; return this; }
        public UserBuilder passwordHash(String passwordHash) { this.passwordHash = passwordHash; return this; }

        private String passwordHash;

        public User build() {
            User user = new User();
            user.id = this.id;
            user.username = this.username;
            user.email = this.email;
            user.ssoId = this.ssoId;
            user.firstName = this.firstName;
            user.lastName = this.lastName;
            user.role = this.role;
            user.active = this.active;
            user.department = this.department;
            user.studentId = this.studentId;
            user.gitlabUserId = this.gitlabUserId;
            user.passwordHash = this.passwordHash;
            return user;
        }
    }

    public enum Role {
        STUDENT, TEACHER, ADMIN
    }
}
