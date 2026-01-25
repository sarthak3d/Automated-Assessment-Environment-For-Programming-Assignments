ALTER TABLE test_modules ADD COLUMN service_url VARCHAR(500);
ALTER TABLE test_modules ALTER COLUMN docker_image DROP NOT NULL;

UPDATE test_modules SET
    service_url = 'http://correctness-evaluator:8080/evaluate',
    docker_image = NULL,
    docker_tag = NULL
WHERE name = 'correctness_evaluator';

UPDATE test_modules SET
    service_url = 'http://performance-analyzer:8080/evaluate',
    docker_image = NULL,
    docker_tag = NULL
WHERE name = 'performance_analyzer';

UPDATE test_modules SET
    service_url = 'http://code-quality:8080/evaluate',
    docker_image = NULL,
    docker_tag = NULL
WHERE name = 'code_quality';

UPDATE test_modules SET
    service_url = 'http://ai-plagiarism:8080/evaluate',
    docker_image = NULL,
    docker_tag = NULL
WHERE name = 'ai_plagiarism';

UPDATE test_modules SET
    service_url = 'http://error-evaluator:8080/evaluate',
    docker_image = NULL,
    docker_tag = NULL
WHERE name = 'error_evaluator';
