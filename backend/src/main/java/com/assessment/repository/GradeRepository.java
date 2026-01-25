package com.assessment.repository;

import com.assessment.model.Grade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GradeRepository extends JpaRepository<Grade, UUID> {

    Optional<Grade> findBySubmissionId(UUID submissionId);

    Page<Grade> findByStudentId(UUID studentId, Pageable pageable);

    Page<Grade> findByAssignmentId(UUID assignmentId, Pageable pageable);

    Optional<Grade> findByStudentIdAndAssignmentId(UUID studentId, UUID assignmentId);

    @Query("SELECT g FROM Grade g WHERE g.student.id = :studentId AND g.assignment.id = :assignmentId " +
           "ORDER BY g.createdAt DESC")
    List<Grade> findLatestGrades(@Param("studentId") UUID studentId, 
                                 @Param("assignmentId") UUID assignmentId,
                                 Pageable pageable);

    default Optional<Grade> findLatestGrade(UUID studentId, UUID assignmentId) {
        List<Grade> grades = findLatestGrades(studentId, assignmentId, Pageable.ofSize(1));
        return grades.isEmpty() ? Optional.empty() : Optional.of(grades.get(0));
    }

    @Query("SELECT AVG(g.percentageScore) FROM Grade g WHERE g.assignment.id = :assignmentId")
    Optional<Double> findAverageScoreByAssignment(@Param("assignmentId") UUID assignmentId);

    @Query("SELECT g.letterGrade, COUNT(g) FROM Grade g WHERE g.assignment.id = :assignmentId GROUP BY g.letterGrade")
    List<Object[]> findGradeDistributionByAssignment(@Param("assignmentId") UUID assignmentId);

    @Query("SELECT AVG(g.percentageScore) FROM Grade g " +
           "WHERE g.assignment.course.id = :courseId AND g.student.id = :studentId")
    Optional<Double> findStudentAverageInCourse(@Param("courseId") UUID courseId, @Param("studentId") UUID studentId);
}
