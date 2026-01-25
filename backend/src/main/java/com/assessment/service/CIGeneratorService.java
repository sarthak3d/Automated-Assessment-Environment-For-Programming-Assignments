package com.assessment.service;

import com.assessment.model.*;
import com.assessment.repository.TestModuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CIGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(CIGeneratorService.class);

    private final TestModuleRepository testModuleRepository;

    public CIGeneratorService(TestModuleRepository testModuleRepository) {
        this.testModuleRepository = testModuleRepository;
    }

    @Value("${assessment.module-gateway.url:http://module-gateway}")
    private String moduleGatewayUrl;

    @Value("${assessment.module-gateway.ci-runner-image:alpine:3.19}")
    private String ciRunnerImage;

    @Value("${assessment.grading.default-timeout-seconds:300}")
    private int defaultTimeoutSeconds;

    @Value("${assessment.judge0.url:http://judge0-server.judge0.svc.cluster.local:2358}")
    private String judge0Url;

    @Value("${assessment.backend-url:http://assessment-assessment-platform-backend.assessment-platform.svc.cluster.local:8080}")
    private String backendUrl;

    public String generateCIYaml(Assignment assignment, List<TestModuleWeight> testModuleWeights) {
        Map<String, Object> ciConfig = new LinkedHashMap<>();

        ciConfig.put("stages", List.of("compile", "evaluate", "error_handling", "grade"));

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("ASSIGNMENT_ID", assignment.getId().toString());
        variables.put("GIT_DEPTH", 1);
        variables.put("MODULE_GATEWAY_URL", moduleGatewayUrl);
        variables.put("BACKEND_URL", backendUrl);
        variables.put("JUDGE0_URL", judge0Url);
        ciConfig.put("variables", variables);

        Map<String, Object> defaultConfig = new LinkedHashMap<>();
        defaultConfig.put("retry", Map.of(
                "max", 2,
                "when", List.of("runner_system_failure", "stuck_or_timeout_failure")));
        ciConfig.put("default", defaultConfig);

        ciConfig.put("compile", buildCompileJob(assignment));
        ciConfig.put("error_evaluator", buildErrorHandlerJob(assignment));

        for (TestModuleWeight weight : testModuleWeights) {
            if (weight.isEnabled()) {
                String jobName = weight.getTestModule().getName().toLowerCase().replace(" ", "_");
                Map<String, Object> job = buildTestModuleJob(assignment, weight);
                ciConfig.put(jobName, job);
            }
        }

        ciConfig.put("grader", buildGraderJob(assignment, testModuleWeights));

        return generateYamlString(ciConfig);
    }

    private Map<String, Object> buildCompileJob(Assignment assignment) {
        Map<String, Object> job = new LinkedHashMap<>();
        job.put("stage", "compile");
        job.put("image", ciRunnerImage);
        job.put("tags", List.of("assessment", "kubernetes"));

        List<String> beforeScript = List.of("apk add --no-cache curl jq coreutils");
        job.put("before_script", beforeScript);

        int languageId = getDefaultLanguageId(assignment);

        List<String> script = new ArrayList<>();
        script.add("echo 'Compiling student code via Judge0...'");
        script.add("cd students/${STUDENT_ID}");
        script.add("echo 'COMPILE_STATUS=failure' > compile_result.txt");
        script.add("SRC_FILE=$(ls *.c *.cpp *.java *.js *.py *.cs 2>/dev/null | head -1 || true)");
        script.add(
                "if [ -z \"$SRC_FILE\" ]; then echo 'No source file found'; echo 'COMPILE_STATUS=failure' > compile_result.txt; exit 0; fi");
        script.add("EXT=${SRC_FILE##*.}");
        script.add(
                "case \"$EXT\" in c) LANG_ID=50;; py) LANG_ID=71;; cpp|cc) LANG_ID=54;; java) LANG_ID=62;; js) LANG_ID=63;; cs) LANG_ID=51;; *) LANG_ID=71;; esac");
        script.add("echo \"Detected language_id=$LANG_ID for file $SRC_FILE\"");
        script.add("B64_SRC=$(base64 -w0 \"$SRC_FILE\")");
        script.add(
                "PAYLOAD=$(jq -n --arg src \"$B64_SRC\" --argjson lid \"$LANG_ID\" '{source_code: $src, language_id: $lid}')");
        script.add(
                "RESULT=$(curl -s -X POST \"${JUDGE0_URL}/submissions?base64_encoded=true&wait=true\" -H 'Content-Type: application/json' -d \"$PAYLOAD\" || echo '{}')");
        script.add("echo \"Judge0 result: $RESULT\"");
        script.add("STATUS_ID=$(echo \"$RESULT\" | jq -r '.status.id // 0')");
        script.add("COMPILE_OUT=$(echo \"$RESULT\" | jq -r '.compile_output // empty')");
        script.add("STDERR=$(echo \"$RESULT\" | jq -r '.stderr // empty')");
        script.add("if [ \"$STATUS_ID\" -ge 6 ]; then");
        script.add("  echo \"Compilation failed (status=$STATUS_ID): $COMPILE_OUT $STDERR\"");
        script.add("  echo 'COMPILE_STATUS=failure' > compile_result.txt");
        script.add("else");
        script.add("  echo 'COMPILE_STATUS=success' > compile_result.txt");
        script.add("  echo 'Compilation successful'");
        script.add("fi");
        job.put("script", script);

        Map<String, Object> artifacts = new LinkedHashMap<>();
        artifacts.put("paths", List.of("students/${STUDENT_ID}/compile_result.txt"));
        artifacts.put("reports", Map.of("dotenv", "students/${STUDENT_ID}/compile_result.txt"));
        artifacts.put("expire_in", "1 day");
        artifacts.put("when", "always");
        job.put("artifacts", artifacts);

        job.put("timeout", "5m");
        job.put("allow_failure", true);

        return job;
    }

    private Map<String, Object> buildErrorHandlerJob(Assignment assignment) {
        Map<String, Object> job = new LinkedHashMap<>();
        job.put("stage", "error_handling");
        job.put("image", ciRunnerImage);
        job.put("tags", List.of("assessment", "kubernetes"));

        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("if", "$COMPILE_STATUS == \"failure\"");
        rules.put("when", "always");
        job.put("rules", List.of(rules));

        List<String> beforeScript = List.of("apk add --no-cache curl");
        job.put("before_script", beforeScript);

        int languageId = getDefaultLanguageId(assignment);

        List<String> script = new ArrayList<>();
        script.add("echo 'Analyzing compilation errors...'");
        script.add("tar czf /tmp/code.tar.gz -C students/${STUDENT_ID}/ .");
        script.add(
                "curl -sf -X POST \"${MODULE_GATEWAY_URL}/api/modules/error-evaluator/evaluate\" -F \"code=@/tmp/code.tar.gz\" -F 'config={\"language_id\": "
                        + languageId + "}' -o error_evaluation.json || echo '{}' > error_evaluation.json");
        script.add("cat error_evaluation.json");
        job.put("script", script);

        job.put("artifacts", Map.of(
                "paths", List.of("error_evaluation.json"),
                "expire_in", "1 day",
                "reports", Map.of("dotenv", "error_evaluation.env")));

        job.put("timeout", "10m");

        return job;
    }

    private Map<String, Object> buildTestModuleJob(Assignment assignment, TestModuleWeight weight) {
        TestModule module = weight.getTestModule();
        Map<String, Object> job = new LinkedHashMap<>();

        job.put("stage", "evaluate");
        job.put("image", ciRunnerImage);
        job.put("tags", List.of("assessment", "kubernetes"));

        job.put("needs", List.of(
                Map.of("job", "compile", "artifacts", true)));

        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("if", "$COMPILE_STATUS == 'success'");
        rules.put("when", "on_success");
        job.put("rules", List.of(rules));

        List<String> beforeScript = List.of("apk add --no-cache curl");
        job.put("before_script", beforeScript);

        List<String> script = buildModuleScript(module, assignment, weight);
        job.put("script", script);

        String artifactName = module.getName().toLowerCase().replace(" ", "_") + "_result.json";
        job.put("artifacts", Map.of(
                "paths", List.of(artifactName),
                "expire_in", "1 day"));

        int timeout = weight.getCustomTimeoutSeconds() != null ? weight.getCustomTimeoutSeconds()
                : module.getDefaultTimeoutSeconds();
        job.put("timeout", timeout + "s");

        Map<String, Object> resources = new LinkedHashMap<>();
        int memoryLimit = weight.getCustomMemoryLimitMb() != null ? weight.getCustomMemoryLimitMb()
                : module.getDefaultMemoryLimitMb();
        resources.put("limits", Map.of("memory", memoryLimit + "Mi"));
        job.put("resource_group", module.getName());

        return job;
    }

    private List<String> buildModuleScript(TestModule module, Assignment assignment, TestModuleWeight weight) {
        List<String> script = new ArrayList<>();
        script.add("echo 'Running " + module.getDisplayName() + "...'");
        script.add("tar czf /tmp/code.tar.gz -C students/${STUDENT_ID}/ .");

        String artifactName = module.getName().toLowerCase().replace(" ", "_") + "_result.json";
        String moduleRoute = module.getName().replace("_", "-");
        String configJson = buildModuleConfig(module, assignment, weight);

        script.add("curl -sf -X POST \"${MODULE_GATEWAY_URL}/api/modules/" + moduleRoute
                + "/evaluate\" -F \"code=@/tmp/code.tar.gz\" -F 'config=" + configJson + "' -o " + artifactName);
        script.add("echo 'Result:'");
        script.add("cat " + artifactName);

        return script;
    }

    private String buildModuleConfig(TestModule module, Assignment assignment, TestModuleWeight weight) {
        int languageId = getDefaultLanguageId(assignment);
        String customConfig = weight.getCustomConfig() != null ? weight.getCustomConfig() : "{}";

        return switch (module.getName()) {
            case "correctness_evaluator" -> "{\"language_id\": " + languageId + "}";
            case "performance_analyzer" -> "{\"language_id\": " + languageId + ", \"format\": \"json\"}";
            case "code_quality" -> "{\"language_id\": " + languageId + "}";
            case "ai_plagiarism" -> "{\"language_id\": " + languageId + ", \"model\": \"xgboost\"}";
            case "error_evaluator" -> "{\"language_id\": " + languageId + "}";
            default -> customConfig;
        };
    }

    private Map<String, Object> buildGraderJob(Assignment assignment, List<TestModuleWeight> weights) {
        Map<String, Object> job = new LinkedHashMap<>();
        job.put("stage", "grade");
        job.put("image", ciRunnerImage);
        job.put("tags", List.of("assessment", "kubernetes"));

        List<Map<String, Object>> needs = new ArrayList<>();
        for (TestModuleWeight weight : weights) {
            if (weight.isEnabled()) {
                String jobName = weight.getTestModule().getName().toLowerCase().replace(" ", "_");
                needs.add(Map.of("job", jobName, "artifacts", true, "optional", true));
            }
        }
        needs.add(Map.of("job", "error_evaluator", "artifacts", true, "optional", true));
        job.put("needs", needs);

        job.put("when", "always");

        List<String> beforeScript = List.of("apk add --no-cache curl jq");
        job.put("before_script", beforeScript);

        List<String> script = buildGraderScript(assignment, weights);
        job.put("script", script);

        job.put("artifacts", Map.of(
                "paths", List.of("final_grade.json"),
                "expire_in", "30 days"));

        job.put("timeout", "5m");

        return job;
    }

    private List<String> buildGraderScript(Assignment assignment, List<TestModuleWeight> weights) {
        List<String> script = new ArrayList<>();
        script.add("echo 'Calculating final grade...'");

        script.add("RESULTS='{}'");
        script.add("for f in *_result.json; do");
        script.add("  [ -f \"$f\" ] || continue");
        script.add("  MODULE=$(echo \"$f\" | sed 's/_result\\.json$//')");
        script.add(
                "  RESULTS=$(echo \"$RESULTS\" | jq --arg mod \"$MODULE\" --slurpfile data \"$f\" '. + {($mod): $data[0]}')");
        script.add("done");

        StringBuilder weightsJson = new StringBuilder("{");
        List<String> weightPairs = new ArrayList<>();
        for (TestModuleWeight weight : weights) {
            if (weight.isEnabled() && weight.getTestModule().isUseForGrading()) {
                weightPairs.add("\"" + weight.getTestModule().getName() + "\": " + weight.getWeight());
            }
        }
        weightsJson.append(String.join(", ", weightPairs)).append("}");

        script.add("WEIGHTS='" + weightsJson + "'");

        script.add("jq -n --arg assignment_id \"" + assignment.getId()
                + "\" --arg pipeline_id \"${CI_PIPELINE_ID}\" --arg student_id \"${STUDENT_ID}\" --argjson results \"$RESULTS\" --argjson weights \"$WEIGHTS\" '{assignment_id: $assignment_id, pipeline_id: ($pipeline_id | tonumber), student_id: $student_id, module_results: $results, weights: $weights}' > final_grade.json");

        script.add("echo 'Grade payload:'");
        script.add("cat final_grade.json");

        script.add("echo 'Sending results to backend...'");
        script.add(
                "curl -s -X POST \"${BACKEND_URL}/api/v1/webhooks/pipeline-complete\" -H 'Content-Type: application/json' -d @final_grade.json || echo 'WARNING: Failed to send grade to backend, will retry via webhook'");

        return script;
    }

    private int getDefaultLanguageId(Assignment assignment) {
        if (assignment.getQuestions().isEmpty()) {
            return 71;
        }
        return assignment.getQuestions().get(0).getLanguageId();
    }

    private String generateYamlString(Map<String, Object> config) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setIndicatorIndent(0);

        Yaml yaml = new Yaml(options);
        return yaml.dump(config);
    }
}
