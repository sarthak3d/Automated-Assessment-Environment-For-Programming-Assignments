package com.assessment.repository;

import com.assessment.model.TestModule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TestModuleRepository extends JpaRepository<TestModule, UUID> {

    Optional<TestModule> findByName(String name);

    boolean existsByName(String name);

    Page<TestModule> findByActiveTrue(Pageable pageable);

    Page<TestModule> findByPredefinedAndActiveTrue(boolean predefined, Pageable pageable);

    List<TestModule> findByPredefinedTrueAndActiveTrue();

    Page<TestModule> findByUseForGradingTrueAndActiveTrue(Pageable pageable);

    Page<TestModule> findByOutputTypeAndActiveTrue(TestModule.OutputType outputType, Pageable pageable);

    Page<TestModule> findByCreatedById(UUID createdById, Pageable pageable);
}
