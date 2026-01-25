-- Flyway Migration: Seed predefined test modules

INSERT INTO test_modules (id, name, display_name, description, docker_image, docker_tag, output_type, use_for_grading, predefined, default_timeout_seconds, default_memory_limit_mb, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'correctness_evaluator', 'Correctness Evaluator', 
     'Validates code output against expected test cases', 
     'assessment-platform/correctness-evaluator', 'latest', 'PASS_FAIL', 
     true, true, 300, 512, NOW(), NOW()),
    
    (gen_random_uuid(), 'performance_analyzer', 'Performance Analyzer', 
     'Analyzes time and space complexity using bigO-analyzer', 
     'assessment-platform/performance-analyzer', 'latest', 'TEXT_MATCH', 
     true, true, 600, 2048, NOW(), NOW()),
    
    (gen_random_uuid(), 'code_quality', 'Code Quality Evaluator', 
     'Checks code for best practices and coding standards', 
     'assessment-platform/code-quality', 'latest', 'PERCENTAGE', 
     true, true, 300, 1024, NOW(), NOW()),
    
    (gen_random_uuid(), 'ai_plagiarism', 'AI Plagiarism Checker', 
     'Detects AI-generated code using machine learning classification', 
     'assessment-platform/ai-plagiarism', 'latest', 'PERCENTAGE', 
     true, true, 120, 4096, NOW(), NOW()),
    
    (gen_random_uuid(), 'error_evaluator', 'Error Evaluator', 
     'Uses LLM and compiler to provide detailed error analysis and suggestions', 
     'assessment-platform/error-evaluator', 'latest', 'SUGGESTION_ONLY', 
     false, true, 600, 2048, NOW(), NOW());

UPDATE test_modules SET min_value = 0, max_value = 100 WHERE output_type = 'PERCENTAGE';

INSERT INTO test_module_valid_texts (test_module_id, valid_text)
SELECT id, 'O(1)' FROM test_modules WHERE name = 'performance_analyzer'
UNION ALL
SELECT id, 'O(log n)' FROM test_modules WHERE name = 'performance_analyzer'
UNION ALL
SELECT id, 'O(n)' FROM test_modules WHERE name = 'performance_analyzer'
UNION ALL
SELECT id, 'O(n log n)' FROM test_modules WHERE name = 'performance_analyzer'
UNION ALL
SELECT id, 'O(n^2)' FROM test_modules WHERE name = 'performance_analyzer'
UNION ALL
SELECT id, 'O(n^3)' FROM test_modules WHERE name = 'performance_analyzer'
UNION ALL
SELECT id, 'O(2^n)' FROM test_modules WHERE name = 'performance_analyzer'
UNION ALL
SELECT id, 'O(n!)' FROM test_modules WHERE name = 'performance_analyzer';
