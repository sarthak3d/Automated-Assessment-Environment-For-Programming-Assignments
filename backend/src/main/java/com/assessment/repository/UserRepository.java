package com.assessment.repository;

import com.assessment.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findBySsoId(String ssoId);

    Optional<User> findByGitlabUserId(Long gitlabUserId);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsBySsoId(String ssoId);

    Page<User> findByRole(User.Role role, Pageable pageable);

    Page<User> findByRoleAndActiveTrue(User.Role role, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.role = :role AND " +
           "(LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> searchByRoleAndTerm(@Param("role") User.Role role, @Param("search") String search, Pageable pageable);

    @Query("SELECT u FROM User u JOIN u.enrolledCourses c WHERE c.id = :courseId")
    Page<User> findStudentsByCourseId(@Param("courseId") UUID courseId, Pageable pageable);

    @Query("SELECT COUNT(u) FROM User u JOIN u.enrolledCourses c WHERE c.id = :courseId")
    long countStudentsByCourseId(@Param("courseId") UUID courseId);
}
