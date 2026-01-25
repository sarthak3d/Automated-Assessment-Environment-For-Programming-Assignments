package com.assessment.repository;

import com.assessment.model.Course;
import com.assessment.model.User;
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
public interface CourseRepository extends JpaRepository<Course, UUID> {

    Optional<Course> findByCode(String code);

    Optional<Course> findByGitlabGroupId(Long gitlabGroupId);

    boolean existsByCode(String code);

    Page<Course> findByActiveTrue(Pageable pageable);

    Page<Course> findByInstructor(User instructor, Pageable pageable);

    Page<Course> findByInstructorAndActiveTrue(User instructor, Pageable pageable);

    @Query("SELECT c FROM Course c JOIN c.enrolledStudents s WHERE s.id = :studentId")
    Page<Course> findByEnrolledStudentId(@Param("studentId") UUID studentId, Pageable pageable);

    @Query("SELECT c FROM Course c JOIN c.enrolledStudents s WHERE s.id = :studentId AND c.active = true")
    Page<Course> findActiveByEnrolledStudentId(@Param("studentId") UUID studentId, Pageable pageable);

    @Query("SELECT c FROM Course c WHERE c.active = true AND " +
           "(LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.code) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Course> searchByTerm(@Param("search") String search, Pageable pageable);

    List<Course> findBySemesterAndYear(String semester, Integer year);

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Course c " +
           "JOIN c.enrolledStudents s WHERE c.id = :courseId AND s.id = :studentId")
    boolean isStudentEnrolled(@Param("courseId") UUID courseId, @Param("studentId") UUID studentId);
}
