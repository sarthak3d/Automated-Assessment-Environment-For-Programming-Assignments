package com.assessment.model;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "test_modules", indexes = {
    @Index(name = "idx_test_modules_name", columnList = "name", unique = true),
    @Index(name = "idx_test_modules_output_type", columnList = "outputType")
})
@EntityListeners(AuditingEntityListener.class)
public class TestModule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false, length = 255)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 255)
    private String dockerImage;

    @Column(length = 100)
    private String dockerTag;

    @Column(length = 500)
    private String serviceUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OutputType outputType;

    @Column(nullable = false)
    private boolean useForGrading = true;

    @Column(nullable = false)
    private boolean predefined = true;

    @Column
    private Double minValue;

    @Column
    private Double maxValue;

    @ElementCollection
    @CollectionTable(name = "test_module_valid_texts", joinColumns = @JoinColumn(name = "test_module_id"))
    @Column(name = "valid_text", length = 255)
    private List<String> validTextOutputs;

    @Column(nullable = false)
    private Integer defaultTimeoutSeconds = 300;

    @Column(nullable = false)
    private Integer defaultMemoryLimitMb = 2048;

    @Column(columnDefinition = "TEXT")
    private String configSchema;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    public TestModule() {}

    public String getFullImageName() {
        if (dockerImage == null || dockerImage.isEmpty()) {
            return null;
        }
        if (dockerTag != null && !dockerTag.isEmpty()) {
            return dockerImage + ":" + dockerTag;
        }
        return dockerImage + ":latest";
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDockerImage() { return dockerImage; }
    public void setDockerImage(String dockerImage) { this.dockerImage = dockerImage; }
    public String getDockerTag() { return dockerTag; }
    public void setDockerTag(String dockerTag) { this.dockerTag = dockerTag; }
    public String getServiceUrl() { return serviceUrl; }
    public void setServiceUrl(String serviceUrl) { this.serviceUrl = serviceUrl; }
    public OutputType getOutputType() { return outputType; }
    public void setOutputType(OutputType outputType) { this.outputType = outputType; }
    public boolean isUseForGrading() { return useForGrading; }
    public void setUseForGrading(boolean useForGrading) { this.useForGrading = useForGrading; }
    public boolean isPredefined() { return predefined; }
    public void setPredefined(boolean predefined) { this.predefined = predefined; }
    public Double getMinValue() { return minValue; }
    public void setMinValue(Double minValue) { this.minValue = minValue; }
    public Double getMaxValue() { return maxValue; }
    public void setMaxValue(Double maxValue) { this.maxValue = maxValue; }
    public List<String> getValidTextOutputs() { return validTextOutputs; }
    public void setValidTextOutputs(List<String> validTextOutputs) { this.validTextOutputs = validTextOutputs; }
    public Integer getDefaultTimeoutSeconds() { return defaultTimeoutSeconds; }
    public void setDefaultTimeoutSeconds(Integer defaultTimeoutSeconds) { this.defaultTimeoutSeconds = defaultTimeoutSeconds; }
    public Integer getDefaultMemoryLimitMb() { return defaultMemoryLimitMb; }
    public void setDefaultMemoryLimitMb(Integer defaultMemoryLimitMb) { this.defaultMemoryLimitMb = defaultMemoryLimitMb; }
    public String getConfigSchema() { return configSchema; }
    public void setConfigSchema(String configSchema) { this.configSchema = configSchema; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public enum OutputType {
        PASS_FAIL, PERCENTAGE, NUMBER_RANGE, TEXT_MATCH, SUGGESTION_ONLY
    }
}
