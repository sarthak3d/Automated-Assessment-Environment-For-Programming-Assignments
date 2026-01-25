package com.assessment.repository;

import com.assessment.model.Assignment;
import com.assessment.model.Course;
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
public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    Page<Assignment> findByCourse(Course course, Pageable pageable);

    Page<Assignment> findByCourseId(UUID courseId, Pageable pageable);

    Page<Assignment> findByCourseIdAndStatus(UUID courseId, Assignment.Status status, Pageable pageable);

    Optional<Assignment> findByGitlabProjectId(Long gitlabProjectId);

    boolean existsByIdAndCourseInstructorId(UUID assignmentId, UUID instructorId);

    @Query("SELECT a FROM Assignment a WHERE a.course.id = :courseId AND a.status = 'PUBLISHED' AND a.dueDate > :now")
    Page<Assignment> findActiveAssignments(@Param("courseId") UUID courseId, @Param("now") Instant now, Pageable pageable);

    @Query("SELECT a FROM Assignment a WHERE a.course.id = :courseId AND a.status = 'PUBLISHED'")
    Page<Assignment> findPublishedByCourseId(@Param("courseId") UUID courseId, Pageable pageable);

    @Query("SELECT a FROM Assignment a JOIN a.course c JOIN c.enrolledStudents s " +
           "WHERE s.id = :studentId AND a.status = 'PUBLISHED'")
    Page<Assignment> findPublishedAssignmentsForStudent(@Param("studentId") UUID studentId, Pageable pageable);

    List<Assignment> findByDueDateBeforeAndStatus(Instant dueDate, Assignment.Status status);

    @Query("SELECT COUNT(a) FROM Assignment a WHERE a.course.id = :courseId AND a.status = :status")
    long countByCourseIdAndStatus(@Param("courseId") UUID courseId, @Param("status") Assignment.Status status);
}
