package com.assessment.controller;

import com.assessment.dto.CourseDto;
import com.assessment.dto.UserDto;
import com.assessment.model.Course;
import com.assessment.model.User;
import com.assessment.repository.CourseRepository;
import com.assessment.repository.UserRepository;
import com.assessment.service.GitLabService;
import jakarta.validation.Valid;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.AccessLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses")
public class CourseController {

    private static final Logger log = LoggerFactory.getLogger(CourseController.class);

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final GitLabService gitLabService;

    public CourseController(CourseRepository courseRepository, UserRepository userRepository,
                            GitLabService gitLabService) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.gitLabService = gitLabService;
    }

    @GetMapping
    public ResponseEntity<Page<CourseDto>> listCourses(
            @AuthenticationPrincipal User currentUser,
            Pageable pageable) {
        
        Page<Course> courses = switch (currentUser.getRole()) {
            case ADMIN -> courseRepository.findByActiveTrue(pageable);
            case TEACHER -> courseRepository.findByInstructorAndActiveTrue(currentUser, pageable);
            case STUDENT -> courseRepository.findActiveByEnrolledStudentId(currentUser.getId(), pageable);
        };

        return ResponseEntity.ok(courses.map(CourseDto::fromEntity));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CourseDto> getCourse(@PathVariable UUID id) {
        return courseRepository.findById(id)
            .map(CourseDto::fromEntity)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<CourseDto> createCourse(
            @Valid @RequestBody CreateCourseRequest request,
            @AuthenticationPrincipal User currentUser) {

        if (courseRepository.existsByCode(request.code())) {
            return ResponseEntity.badRequest().build();
        }

        Course course = Course.builder()
            .code(request.code())
            .name(request.name())
            .description(request.description())
            .instructor(currentUser)
            .semester(request.semester())
            .year(request.year())
            .active(true)
            .build();

        try {
            Long gitlabGroupId = gitLabService.createCourseGroup(course);
            course.setGitlabGroupId(gitlabGroupId);

            if (currentUser.getGitlabUserId() != null) {
                gitLabService.addUserToGroup(gitlabGroupId, currentUser.getGitlabUserId(), AccessLevel.OWNER);
            }
        } catch (GitLabApiException e) {
            log.error("Failed to create GitLab group for course {}: {}", request.code(), e.getMessage());
        }

        Course saved = courseRepository.save(course);
        return ResponseEntity.created(URI.create("/api/v1/courses/" + saved.getId()))
            .body(CourseDto.fromEntity(saved));
    }

    @PostMapping("/{id}/enroll")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @Transactional
    public ResponseEntity<Void> enrollStudent(
            @PathVariable UUID id,
            @RequestBody EnrollStudentRequest request) {

        Course course = courseRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Course not found"));
        User student = userRepository.findById(request.studentId())
            .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        if (student.getRole() != User.Role.STUDENT) {
            return ResponseEntity.badRequest().build();
        }

        course.addStudent(student);
        courseRepository.save(course);

        if (course.getGitlabGroupId() != null && student.getGitlabUserId() != null) {
            try {
                gitLabService.addUserToGroup(
                    course.getGitlabGroupId(),
                    student.getGitlabUserId(),
                    AccessLevel.DEVELOPER
                );
            } catch (GitLabApiException e) {
                log.error("Failed to add student to GitLab group: {}", e.getMessage());
            }
        }

        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/students")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<Page<UserDto>> listStudents(
            @PathVariable UUID id,
            Pageable pageable) {
        
        Page<User> students = userRepository.findStudentsByCourseId(id, pageable);
        return ResponseEntity.ok(students.map(UserDto::fromEntity));
    }

    public record CreateCourseRequest(
        String code,
        String name,
        String description,
        String semester,
        Integer year
    ) {}

    public record EnrollStudentRequest(UUID studentId) {}
}
