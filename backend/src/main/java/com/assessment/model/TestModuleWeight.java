package com.assessment.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "test_module_weights", indexes = {
    @Index(name = "idx_weights_assignment", columnList = "assignment_id"),
    @Index(name = "idx_weights_module", columnList = "test_module_id")
})
public class TestModuleWeight {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_module_id", nullable = false)
    private TestModule testModule;

    @Column(nullable = false)
    private Double weight;

    @Column(nullable = false)
    private Integer orderIndex = 0;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(columnDefinition = "TEXT")
    private String customConfig;

    @Column
    private Integer customTimeoutSeconds;

    @Column
    private Integer customMemoryLimitMb;

    public TestModuleWeight() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Assignment getAssignment() { return assignment; }
    public void setAssignment(Assignment assignment) { this.assignment = assignment; }
    public TestModule getTestModule() { return testModule; }
    public void setTestModule(TestModule testModule) { this.testModule = testModule; }
    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }
    public Integer getOrderIndex() { return orderIndex; }
    public void setOrderIndex(Integer orderIndex) { this.orderIndex = orderIndex; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getCustomConfig() { return customConfig; }
    public void setCustomConfig(String customConfig) { this.customConfig = customConfig; }
    public Integer getCustomTimeoutSeconds() { return customTimeoutSeconds; }
    public void setCustomTimeoutSeconds(Integer customTimeoutSeconds) { this.customTimeoutSeconds = customTimeoutSeconds; }
    public Integer getCustomMemoryLimitMb() { return customMemoryLimitMb; }
    public void setCustomMemoryLimitMb(Integer customMemoryLimitMb) { this.customMemoryLimitMb = customMemoryLimitMb; }

    public static TestModuleWeightBuilder builder() { return new TestModuleWeightBuilder(); }

    public static class TestModuleWeightBuilder {
        private Assignment assignment;
        private TestModule testModule;
        private Double weight;
        private Integer orderIndex = 0;
        private boolean enabled = true;
        private String customConfig;
        private Integer customTimeoutSeconds;
        private Integer customMemoryLimitMb;

        public TestModuleWeightBuilder assignment(Assignment assignment) { this.assignment = assignment; return this; }
        public TestModuleWeightBuilder testModule(TestModule testModule) { this.testModule = testModule; return this; }
        public TestModuleWeightBuilder weight(Double weight) { this.weight = weight; return this; }
        public TestModuleWeightBuilder orderIndex(Integer orderIndex) { this.orderIndex = orderIndex; return this; }
        public TestModuleWeightBuilder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public TestModuleWeightBuilder customConfig(String customConfig) { this.customConfig = customConfig; return this; }
        public TestModuleWeightBuilder customTimeoutSeconds(Integer customTimeoutSeconds) { this.customTimeoutSeconds = customTimeoutSeconds; return this; }
        public TestModuleWeightBuilder customMemoryLimitMb(Integer customMemoryLimitMb) { this.customMemoryLimitMb = customMemoryLimitMb; return this; }

        public TestModuleWeight build() {
            TestModuleWeight w = new TestModuleWeight();
            w.assignment = this.assignment;
            w.testModule = this.testModule;
            w.weight = this.weight;
            w.orderIndex = this.orderIndex;
            w.enabled = this.enabled;
            w.customConfig = this.customConfig;
            w.customTimeoutSeconds = this.customTimeoutSeconds;
            w.customMemoryLimitMb = this.customMemoryLimitMb;
            return w;
        }
    }
}
