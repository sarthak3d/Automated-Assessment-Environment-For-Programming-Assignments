package com.assessment.controller;

import com.assessment.service.PipelineResultService;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final PipelineResultService pipelineResultService;

    public WebhookController(PipelineResultService pipelineResultService) {
        this.pipelineResultService = pipelineResultService;
    }

    @PostMapping("/pipeline")
    public ResponseEntity<Void> handlePipelineWebhook(
            @RequestHeader(value = "X-Gitlab-Token", required = false) String gitlabToken,
            @RequestBody JsonNode payload) {

        String objectKind = payload.path("object_kind").asText();
        if (!"pipeline".equals(objectKind)) {
            log.debug("Ignoring non-pipeline webhook: {}", objectKind);
            return ResponseEntity.ok().build();
        }

        JsonNode objectAttributes = payload.path("object_attributes");
        Long pipelineId = objectAttributes.path("id").asLong();
        String status = objectAttributes.path("status").asText();

        JsonNode project = payload.path("project");
        Long projectId = project.path("id").asLong();

        log.info("Pipeline webhook received: project={}, pipeline={}, status={}", 
            projectId, pipelineId, status);

        pipelineResultService.processPipelineWebhook(projectId, pipelineId, status);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/job")
    public ResponseEntity<Void> handleJobWebhook(
            @RequestHeader(value = "X-Gitlab-Token", required = false) String gitlabToken,
            @RequestBody JsonNode payload) {

        String objectKind = payload.path("object_kind").asText();
        if (!"build".equals(objectKind)) {
            log.debug("Ignoring non-job webhook: {}", objectKind);
            return ResponseEntity.ok().build();
        }

        Long jobId = payload.path("build_id").asLong();
        String status = payload.path("build_status").asText();
        Long pipelineId = payload.path("pipeline_id").asLong();
        Long projectId = payload.path("project_id").asLong();
        String jobName = payload.path("build_name").asText();

        log.debug("Job webhook received: project={}, pipeline={}, job={}, name={}, status={}", 
            projectId, pipelineId, jobId, jobName, status);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/pipeline-complete")
    public ResponseEntity<Void> handlePipelineCompleteCallback(@RequestBody JsonNode payload) {
        log.info("Pipeline complete callback received: {}", payload.path("submission_id"));
        return ResponseEntity.ok().build();
    }
}
