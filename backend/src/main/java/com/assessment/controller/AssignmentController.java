package com.assessment.controller;

import com.assessment.dto.AssignmentDto;
import com.assessment.model.*;
import com.assessment.repository.*;
import com.assessment.service.CIGeneratorService;
import com.assessment.service.GitLabService;
import jakarta.validation.Valid;
import org.gitlab4j.api.GitLabApiException;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/assignments")
public class AssignmentController {

    private static final Logger log = LoggerFactory.getLogger(AssignmentController.class);

    private final AssignmentRepository assignmentRepository;
    private final CourseRepository courseRepository;
    private final TestModuleRepository testModuleRepository;
    private final TestModuleWeightRepository testModuleWeightRepository;
    private final GitLabService gitLabService;
    private final CIGeneratorService ciGeneratorService;

    public AssignmentController(AssignmentRepository assignmentRepository, CourseRepository courseRepository,
                                TestModuleRepository testModuleRepository, TestModuleWeightRepository testModuleWeightRepository,
                                GitLabService gitLabService, CIGeneratorService ciGeneratorService) {
        this.assignmentRepository = assignmentRepository;
        this.courseRepository = courseRepository;
        this.testModuleRepository = testModuleRepository;
        this.testModuleWeightRepository = testModuleWeightRepository;
        this.gitLabService = gitLabService;
        this.ciGeneratorService = ciGeneratorService;
    }

    @GetMapping
    public ResponseEntity<Page<AssignmentDto>> listAssignments(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal User currentUser,
            Pageable pageable) {

        Page<Assignment> assignments;
        if (currentUser.getRole() == User.Role.STUDENT) {
            assignments = assignmentRepository.findByCourseIdAndStatus(
                courseId, Assignment.Status.PUBLISHED, pageable);
        } else {
            assignments = assignmentRepository.findByCourseId(courseId, pageable);
        }

        return ResponseEntity.ok(assignments.map(AssignmentDto::fromEntity));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AssignmentDto> getAssignment(
            @PathVariable UUID courseId,
            @PathVariable UUID id) {
        
        return assignmentRepository.findById(id)
            .filter(a -> a.getCourse().getId().equals(courseId))
            .map(AssignmentDto::fromEntity)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @Transactional
    public ResponseEntity<AssignmentDto> createAssignment(
            @PathVariable UUID courseId,
            @Valid @RequestBody CreateAssignmentRequest request) {

        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new IllegalArgumentException("Course not found"));

        Assignment assignment = Assignment.builder()
            .title(request.title())
            .description(request.description())
            .course(course)
            .dueDate(request.dueDate())
            .maxSubmissions(request.maxSubmissions() != null ? request.maxSubmissions() : 10)
            .allowLateSubmissions(request.allowLateSubmissions() != null ? request.allowLateSubmissions() : false)
            .status(Assignment.Status.DRAFT)
            .build();

        try {
            Long projectId = gitLabService.createAssignmentRepository(
                course, request.title(), request.description());
            assignment.setGitlabProjectId(projectId);
            assignment.setGitlabProjectPath("course-" + course.getCode() + "/" + 
                request.title().toLowerCase().replaceAll("[^a-z0-9\\s]", "").replaceAll("\\s+", "-"));
        } catch (GitLabApiException e) {
            log.error("Failed to create GitLab project: {}", e.getMessage());
        }

        Assignment saved = assignmentRepository.save(assignment);

        if (request.testModuleIds() != null) {
            configureTestModules(saved, request.testModuleIds());
        }

        return ResponseEntity.created(
            URI.create("/api/v1/courses/" + courseId + "/assignments/" + saved.getId())
        ).body(AssignmentDto.fromEntity(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @Transactional
    public ResponseEntity<AssignmentDto> updateAssignment(
            @PathVariable UUID courseId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAssignmentRequest request) {

        Assignment assignment = assignmentRepository.findById(id)
            .filter(a -> a.getCourse().getId().equals(courseId))
            .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

        if (request.title() != null) assignment.setTitle(request.title());
        if (request.description() != null) assignment.setDescription(request.description());
        if (request.dueDate() != null) assignment.setDueDate(request.dueDate());
        if (request.maxSubmissions() != null) assignment.setMaxSubmissions(request.maxSubmissions());
        if (request.allowLateSubmissions() != null) assignment.setAllowLateSubmissions(request.allowLateSubmissions());

        return ResponseEntity.ok(AssignmentDto.fromEntity(assignmentRepository.save(assignment)));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @Transactional
    public ResponseEntity<AssignmentDto> publishAssignment(
            @PathVariable UUID courseId,
            @PathVariable UUID id) {

        Assignment assignment = assignmentRepository.findById(id)
            .filter(a -> a.getCourse().getId().equals(courseId))
            .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

        if (assignment.getStatus() != Assignment.Status.DRAFT) {
            return ResponseEntity.badRequest().build();
        }

        List<TestModuleWeight> weights = testModuleWeightRepository
            .findByAssignmentIdAndEnabledTrue(assignment.getId());

        String ciYaml = ciGeneratorService.generateCIYaml(assignment, weights);
        assignment.setCiConfigYaml(ciYaml);

        if (assignment.getGitlabProjectId() != null) {
            try {
                gitLabService.commitFile(
                    assignment.getGitlabProjectId(),
                    ".gitlab-ci.yml",
                    ciYaml,
                    "Configure CI/CD pipeline for assignment",
                    "main"
                );
            } catch (GitLabApiException e) {
                log.error("Failed to commit CI config: {}", e.getMessage());
            }
        }

        assignment.setStatus(Assignment.Status.PUBLISHED);
        assignment.setPublishedAt(Instant.now());

        return ResponseEntity.ok(AssignmentDto.fromEntity(assignmentRepository.save(assignment)));
    }

    @PostMapping("/{id}/test-modules")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @Transactional
    public ResponseEntity<Void> configureTestModules(
            @PathVariable UUID courseId,
            @PathVariable UUID id,
            @RequestBody List<TestModuleConfigRequest> configs) {

        Assignment assignment = assignmentRepository.findById(id)
            .filter(a -> a.getCourse().getId().equals(courseId))
            .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

        testModuleWeightRepository.deleteByAssignmentId(assignment.getId());

        for (int i = 0; i < configs.size(); i++) {
            TestModuleConfigRequest config = configs.get(i);
            TestModule module = testModuleRepository.findById(config.testModuleId())
                .orElseThrow(() -> new IllegalArgumentException("Test module not found"));

            TestModuleWeight weight = TestModuleWeight.builder()
                .assignment(assignment)
                .testModule(module)
                .weight(config.weight())
                .orderIndex(i)
                .enabled(config.enabled() != null ? config.enabled() : true)
                .customConfig(config.customConfig())
                .customTimeoutSeconds(config.timeoutSeconds())
                .customMemoryLimitMb(config.memoryLimitMb())
                .build();

            testModuleWeightRepository.save(weight);
        }

        return ResponseEntity.ok().build();
    }

    private void configureTestModules(Assignment assignment, List<UUID> testModuleIds) {
        double defaultWeight = 100.0 / testModuleIds.size();

        for (int i = 0; i < testModuleIds.size(); i++) {
            UUID moduleId = testModuleIds.get(i);
            TestModule module = testModuleRepository.findById(moduleId)
                .orElseThrow(() -> new IllegalArgumentException("Test module not found: " + moduleId));

            TestModuleWeight weight = TestModuleWeight.builder()
                .assignment(assignment)
                .testModule(module)
                .weight(defaultWeight)
                .orderIndex(i)
                .enabled(true)
                .build();

            testModuleWeightRepository.save(weight);
        }
    }

    public record CreateAssignmentRequest(
        String title,
        String description,
        Instant dueDate,
        Integer maxSubmissions,
        Boolean allowLateSubmissions,
        List<UUID> testModuleIds
    ) {}

    public record UpdateAssignmentRequest(
        String title,
        String description,
        Instant dueDate,
        Integer maxSubmissions,
        Boolean allowLateSubmissions
    ) {}

    public record TestModuleConfigRequest(
        UUID testModuleId,
        Double weight,
        Boolean enabled,
        String customConfig,
        Integer timeoutSeconds,
        Integer memoryLimitMb
    ) {}
}
