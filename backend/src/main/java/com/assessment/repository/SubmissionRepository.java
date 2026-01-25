package com.assessment.repository;

import com.assessment.model.Submission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    Page<Submission> findByStudentId(UUID studentId, Pageable pageable);

    Page<Submission> findByAssignmentId(UUID assignmentId, Pageable pageable);

    Page<Submission> findByStudentIdAndAssignmentId(UUID studentId, UUID assignmentId, Pageable pageable);

    Optional<Submission> findByPipelineId(Long pipelineId);

    @Query("SELECT s FROM Submission s WHERE s.student.id = :studentId AND s.assignment.id = :assignmentId " +
           "ORDER BY s.attemptNumber DESC")
    List<Submission> findLatestSubmissions(@Param("studentId") UUID studentId, 
                                           @Param("assignmentId") UUID assignmentId, 
                                           Pageable pageable);

    default Optional<Submission> findLatestSubmission(UUID studentId, UUID assignmentId) {
        List<Submission> submissions = findLatestSubmissions(studentId, assignmentId, Pageable.ofSize(1));
        return submissions.isEmpty() ? Optional.empty() : Optional.of(submissions.get(0));
    }

    @Query("SELECT COUNT(s) FROM Submission s WHERE s.student.id = :studentId AND s.assignment.id = :assignmentId")
    int countSubmissionsByStudentAndAssignment(@Param("studentId") UUID studentId, 
                                               @Param("assignmentId") UUID assignmentId);

    @Query("SELECT MAX(s.attemptNumber) FROM Submission s WHERE s.student.id = :studentId AND s.assignment.id = :assignmentId")
    Optional<Integer> findMaxAttemptNumber(@Param("studentId") UUID studentId, 
                                           @Param("assignmentId") UUID assignmentId);

    List<Submission> findByStatusInAndSubmittedAtBefore(List<Submission.Status> statuses, Instant before);

    @Query("SELECT s FROM Submission s WHERE s.status = 'RUNNING' AND s.pipelineStartedAt < :timeout")
    List<Submission> findTimedOutSubmissions(@Param("timeout") Instant timeout);

    @Query("SELECT s.student.id, COUNT(s) FROM Submission s WHERE s.assignment.id = :assignmentId GROUP BY s.student.id")
    List<Object[]> countSubmissionsPerStudentForAssignment(@Param("assignmentId") UUID assignmentId);
}
