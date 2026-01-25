package com.assessment.repository;

import com.assessment.model.TestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TestResultRepository extends JpaRepository<TestResult, UUID> {

    List<TestResult> findBySubmissionId(UUID submissionId);

    List<TestResult> findBySubmissionIdOrderByTestModuleNameAsc(UUID submissionId);

    Optional<TestResult> findByJobId(Long jobId);

    Optional<TestResult> findBySubmissionIdAndTestModuleId(UUID submissionId, UUID testModuleId);

    @Query("SELECT tr FROM TestResult tr WHERE tr.submission.id = :submissionId AND tr.testModule.useForGrading = true")
    List<TestResult> findGradableResultsBySubmissionId(@Param("submissionId") UUID submissionId);

    @Query("SELECT AVG(tr.normalizedScore) FROM TestResult tr " +
           "WHERE tr.submission.assignment.id = :assignmentId AND tr.testModule.id = :testModuleId " +
           "AND tr.normalizedScore IS NOT NULL")
    Optional<Double> findAverageScoreByAssignmentAndTestModule(@Param("assignmentId") UUID assignmentId,
                                                               @Param("testModuleId") UUID testModuleId);

    @Query("SELECT tr.testModule.id, AVG(tr.normalizedScore) FROM TestResult tr " +
           "WHERE tr.submission.assignment.id = :assignmentId AND tr.normalizedScore IS NOT NULL " +
           "GROUP BY tr.testModule.id")
    List<Object[]> findAverageScoresPerTestModuleByAssignment(@Param("assignmentId") UUID assignmentId);
}
