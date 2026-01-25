package com.assessment.repository;

import com.assessment.model.TestModuleWeight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TestModuleWeightRepository extends JpaRepository<TestModuleWeight, UUID> {

    List<TestModuleWeight> findByAssignmentIdOrderByOrderIndexAsc(UUID assignmentId);

    List<TestModuleWeight> findByAssignmentIdAndEnabledTrue(UUID assignmentId);

    void deleteByAssignmentId(UUID assignmentId);
}
