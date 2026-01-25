package com.assessment.controller;

import com.assessment.dto.GradeDto;
import com.assessment.dto.SubmissionDto;
import com.assessment.model.*;
import com.assessment.repository.*;
import com.assessment.service.GitLabService;
import com.assessment.service.GradingService;
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
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/assignments/{assignmentId}/submissions")
public class SubmissionController {

    private static final Logger log = LoggerFactory.getLogger(SubmissionController.class);

    private final SubmissionRepository submissionRepository;
    private final AssignmentRepository assignmentRepository;
    private final CourseRepository courseRepository;
    private final GitLabService gitLabService;
    private final GradingService gradingService;

    public SubmissionController(SubmissionRepository submissionRepository, AssignmentRepository assignmentRepository,
                                CourseRepository courseRepository, GitLabService gitLabService,
                                GradingService gradingService) {
        this.submissionRepository = submissionRepository;
        this.assignmentRepository = assignmentRepository;
        this.courseRepository = courseRepository;
        this.gitLabService = gitLabService;
        this.gradingService = gradingService;
    }

    @GetMapping
    public ResponseEntity<Page<SubmissionDto>> listSubmissions(
            @PathVariable UUID courseId,
            @PathVariable UUID assignmentId,
            @AuthenticationPrincipal User currentUser,
            Pageable pageable) {

        Page<Submission> submissions;
        if (currentUser.getRole() == User.Role.STUDENT) {
            submissions = submissionRepository.findByStudentIdAndAssignmentId(
                currentUser.getId(), assignmentId, pageable);
        } else {
            submissions = submissionRepository.findByAssignmentId(assignmentId, pageable);
        }

        return ResponseEntity.ok(submissions.map(SubmissionDto::fromEntity));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubmissionDto> getSubmission(
            @PathVariable UUID courseId,
            @PathVariable UUID assignmentId,
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {

        return submissionRepository.findById(id)
            .filter(s -> s.getAssignment().getId().equals(assignmentId))
            .filter(s -> canAccessSubmission(s, currentUser))
            .map(SubmissionDto::fromEntity)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createSubmission(
            @PathVariable UUID courseId,
            @PathVariable UUID assignmentId,
            @Valid @RequestBody CreateSubmissionRequest request,
            @AuthenticationPrincipal User currentUser) {

        Assignment assignment = assignmentRepository.findById(assignmentId)
            .filter(a -> a.getCourse().getId().equals(courseId))
            .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

        if (assignment.getStatus() != Assignment.Status.PUBLISHED) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Assignment is not open for submissions"));
        }

        Instant now = Instant.now();
        if (!assignment.isAllowLateSubmissions() && now.isAfter(assignment.getDueDate())) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Assignment deadline has passed"));
        }

        int submissionCount = submissionRepository.countSubmissionsByStudentAndAssignment(
            currentUser.getId(), assignmentId);
        if (submissionCount >= assignment.getMaxSubmissions()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Maximum number of submissions reached"));
        }

        int attemptNumber = submissionRepository.findMaxAttemptNumber(currentUser.getId(), assignmentId)
            .orElse(0) + 1;

        Submission submission = Submission.builder()
            .student(currentUser)
            .assignment(assignment)
            .attemptNumber(attemptNumber)
            .status(Submission.Status.PENDING)
            .build();

        submission = submissionRepository.save(submission);

        if (assignment.getGitlabProjectId() != null) {
            try {
                String studentFolder = "students/" + currentUser.getId();
                
                if (currentUser.getGitlabUserId() != null) {
                    gitLabService.addUserToProject(
                        assignment.getGitlabProjectId(),
                        currentUser.getGitlabUserId(),
                        AccessLevel.DEVELOPER
                    );
                }

                for (Map.Entry<String, String> file : request.files().entrySet()) {
                    String filePath = studentFolder + "/" + file.getKey();
                    gitLabService.commitFile(
                        assignment.getGitlabProjectId(),
                        filePath,
                        file.getValue(),
                        "Submission attempt " + attemptNumber + " by " + currentUser.getUsername(),
                        "main"
                    );
                }

                Long pipelineId = gitLabService.triggerPipeline(
                    assignment.getGitlabProjectId(), "main",
                    Map.of("STUDENT_ID", currentUser.getId().toString()));
                submission.setPipelineId(pipelineId);
                submission.setStatus(Submission.Status.QUEUED);

            } catch (GitLabApiException e) {
                log.error("Failed to process submission: {}", e.getMessage());
                submission.setStatus(Submission.Status.ERROR);
                submission.setPipelineLogs("Failed to submit: " + e.getMessage());
            }

            submissionRepository.save(submission);
        }

        return ResponseEntity.created(
            URI.create("/api/v1/courses/" + courseId + "/assignments/" + assignmentId + 
                       "/submissions/" + submission.getId())
        ).body(SubmissionDto.fromEntity(submission));
    }

    @GetMapping("/{id}/grade")
    public ResponseEntity<?> getSubmissionGrade(
            @PathVariable UUID courseId,
            @PathVariable UUID assignmentId,
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {

        Submission submission = submissionRepository.findById(id)
            .filter(s -> s.getAssignment().getId().equals(assignmentId))
            .filter(s -> canAccessSubmission(s, currentUser))
            .orElse(null);

        if (submission == null) {
            return ResponseEntity.notFound().build();
        }

        if (submission.getGrade() == null) {
            return ResponseEntity.ok(Map.of(
                "status", "pending",
                "message", "Grade not yet calculated"
            ));
        }

        return ResponseEntity.ok(GradeDto.fromEntity(submission.getGrade()));
    }

    @PostMapping("/{id}/regrade")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<Void> triggerRegrade(
            @PathVariable UUID courseId,
            @PathVariable UUID assignmentId,
            @PathVariable UUID id) {

        Submission submission = submissionRepository.findById(id)
            .filter(s -> s.getAssignment().getId().equals(assignmentId))
            .orElseThrow(() -> new IllegalArgumentException("Submission not found"));

        gradingService.triggerGrading(submission.getId());
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/my-latest")
    public ResponseEntity<?> getMyLatestSubmission(
            @PathVariable UUID courseId,
            @PathVariable UUID assignmentId,
            @AuthenticationPrincipal User currentUser) {

        return submissionRepository.findLatestSubmission(currentUser.getId(), assignmentId)
            .map(SubmissionDto::fromEntity)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.noContent().build());
    }

    private boolean canAccessSubmission(Submission submission, User user) {
        if (user.getRole() == User.Role.ADMIN) {
            return true;
        }
        if (user.getRole() == User.Role.TEACHER) {
            return assignmentRepository.existsByIdAndCourseInstructorId(
                submission.getAssignment().getId(), user.getId());
        }
        return submission.getStudent().getId().equals(user.getId());
    }

    public record CreateSubmissionRequest(Map<String, String> files) {}
}
